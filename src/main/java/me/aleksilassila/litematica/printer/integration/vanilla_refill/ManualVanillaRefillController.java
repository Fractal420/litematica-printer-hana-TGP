package me.aleksilassila.litematica.printer.integration.vanilla_refill;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.core.action.ResourceLease;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.enums.EnderChestCountType;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.InventoryUtilsAccessor;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.printer.PlayerLook;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import me.aleksilassila.litematica.printer.utils.CarriedItemUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.MessageUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.NetworkUtils;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
//#if MC > 260100
import net.minecraft.world.inventory.ContainerInput;
//#else
//$$ import net.minecraft.world.inventory.ClickType;
//#endif
//#if MC >= 12006
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
//#endif
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ManualVanillaRefillController implements RuntimeComponent {
    private static final String LEASE_OWNER = "manual_vanilla_refill";
    private static final int GLOBAL_TIMEOUT_TICKS = 400;
    private static final int PLACE_HEIGHT = 2;
    private static final int LAVA_SAFE_DISTANCE = 3;
    private static final int PLACE_SETTLE_TICKS = 12;
    private static final int PLACE_CONFIRM_TICKS = 60;
    private static final int MAX_OPEN_ATTEMPTS = 1;
    private static final int CONTENT_WAIT_TICKS = 60;
    private static final int CLOSE_WAIT_TICKS = 40;
    private static final int POST_CLOSE_TICKS = 3;
    private static final int CLICK_DELAY = 2;
    private static final int POST_BREAK_MOVEMENT_LOCK_TICKS = 30;
    private static int toolHotbarSlot(RefillKind kind) {
        int slot = switch (kind) {
            case NETHERITE_PICKAXE -> Configs.Special.MANUAL_VANILLA_REFILL_PICKAXE_SLOT.getIntegerValue();
            case NETHERITE_SHOVEL -> Configs.Special.MANUAL_VANILLA_REFILL_SHOVEL_SLOT.getIntegerValue();
            case NETHERITE_AXE -> Configs.Special.MANUAL_VANILLA_REFILL_AXE_SLOT.getIntegerValue();
            case NETHERITE_HOE -> Configs.Special.MANUAL_VANILLA_REFILL_HOE_SLOT.getIntegerValue();
            default -> 1;
        };
        return clampHotbarIndex(slot - 1);
    }

    private static float toolMinDurabilityFraction(RefillKind kind) {
        int pct = switch (kind) {
            case NETHERITE_PICKAXE -> Configs.Special.MANUAL_VANILLA_REFILL_PICKAXE_DURABILITY.getIntegerValue();
            case NETHERITE_SHOVEL -> Configs.Special.MANUAL_VANILLA_REFILL_SHOVEL_DURABILITY.getIntegerValue();
            case NETHERITE_AXE -> Configs.Special.MANUAL_VANILLA_REFILL_AXE_DURABILITY.getIntegerValue();
            case NETHERITE_HOE -> Configs.Special.MANUAL_VANILLA_REFILL_HOE_DURABILITY.getIntegerValue();
            default -> 10;
        };
        if (pct < 1) pct = 1;
        if (pct > 100) pct = 100;
        return pct / 100.0f;
    }

    private static boolean isToolRefillKind(RefillKind kind) {
        return kind == RefillKind.NETHERITE_PICKAXE
                || kind == RefillKind.NETHERITE_SHOVEL
                || kind == RefillKind.NETHERITE_AXE
                || kind == RefillKind.NETHERITE_HOE;
    }

    private static Item toolItem(RefillKind kind) {
        return switch (kind) {
            case NETHERITE_PICKAXE -> Items.NETHERITE_PICKAXE;
            case NETHERITE_SHOVEL -> Items.NETHERITE_SHOVEL;
            case NETHERITE_AXE -> Items.NETHERITE_AXE;
            case NETHERITE_HOE -> Items.NETHERITE_HOE;
            default -> Items.AIR;
        };
    }

    private static String toolIdFragment(RefillKind kind) {
        return switch (kind) {
            case NETHERITE_PICKAXE -> "netherite_pickaxe";
            case NETHERITE_SHOVEL -> "netherite_shovel";
            case NETHERITE_AXE -> "netherite_axe";
            case NETHERITE_HOE -> "netherite_hoe";
            default -> "";
        };
    }

    private int activeToolHotbarSlot() {
        return toolHotbarSlot(this.activeKind);
    }

    private static int enderHotbarSlot() {
        return clampHotbarIndex(Configs.Special.MANUAL_VANILLA_REFILL_ENDER_SLOT.getIntegerValue() - 1);
    }

    private static int gappleHotbarSlot() {
        return clampHotbarIndex(Configs.Special.MANUAL_VANILLA_REFILL_GAPPLE_SLOT.getIntegerValue() - 1);
    }

    private static int materialHotbarSlotOrAuto() {
        int configured = Configs.Special.MANUAL_VANILLA_REFILL_MATERIAL_SLOT.getIntegerValue();
        if (configured <= 0) {
            return -1;
        }
        return clampHotbarIndex(configured - 1);
    }

    private static int clampHotbarIndex(int index) {
        if (index < 0) {
            return 0;
        }
        if (index > 8) {
            return 8;
        }
        return index;
    }

    private final Minecraft client;
    private Phase phase = Phase.IDLE;
    private final Set<Item> neededItems = new HashSet<>();
    private final Set<Item> pendingNeeded = new HashSet<>();
    private int shulkerInvSlot = -1;
    private Phase clearResumePhase = Phase.EQUIP;
    @Nullable private BlockPos placedPos;
    @Nullable private BlockPos clearPreservePos;
    @Nullable private PlayerLook savedLook;
    private boolean wasSneaking;
    private long deadline;
    private int openAttempts;
    private int settleTicks;
    private int clickCooldown;
    private int expectedContainerId = -1;
    private boolean contentReady;
    private boolean issuedTakeClick;
    private boolean tookItems;
    private boolean takePhaseDone;
    private boolean placementCommitted;
    private boolean lavaWaitMovement;
    private Map<Item, Integer> inventorySnapshot = Map.of();
    private boolean enderMode;
    private int enderInvSlot = -1;
    private final List<BlockPos> placedEnderPositions = new ArrayList<>();
    private int enderPlaceIndex;
    private int enderBreakIndex;
    private final ArrayDeque<RefillTask> queue = new ArrayDeque<>();
    private RefillKind activeKind = RefillKind.MATERIALS;
    private int targetInvSlot = -1;
    private int takeRemaining;
    private boolean pickaxeSwapDone;
    private int pickaxeSwapStage;
    private int pickaxeTempInvSlot = -1;
    private int pickaxeContainerSlot = -1;
    private boolean enderFetchActive;
    private RefillKind enderFetchKind = RefillKind.MATERIALS;
    private int enderFetchTargetSlot = -1;
    private int enderFetchTakeRemaining;
    private long enderFetchCooldownUntilTick;
    private boolean refillFailed;
    private boolean refillLockedOut;
    private final Set<Integer> rejectedEnderPickaxeSlots = new HashSet<>();
    private boolean stashWornPickaxeShulker;
    private boolean enderStashMode;
    private int stashShulkerInvSlot = -1;
    private final Set<Integer> invShulkerSlotsSnapshot = new HashSet<>();

    public ManualVanillaRefillController(Minecraft client) {
        this.client = client;
    }

    public void requestItems(Collection<Item> items) {
        if (!Configs.Special.MANUAL_VANILLA_REFILL.getBooleanValue() || items == null || items.isEmpty()) {
            return;
        }
        if (!Configs.Core.WORK_SWITCH.getBooleanValue()) {
            return;
        }
        if (this.refillLockedOut || this.refillFailed) {
            return;
        }
        Set<Item> needed = new HashSet<>();
        for (Item item : items) {
            if (item != null) {
                needed.add(item);
            }
        }
        if (needed.isEmpty()) {
            return;
        }
        boolean already = isActiveOrQueued(RefillKind.MATERIALS, needed);
        enqueue(RefillKind.MATERIALS, needed, true);
        if (!already) {
            MessageUtils.setOverlayMessage("[Printer Refill] Queued materials: " + needed);
        }
    }

    public void requestItem(Item item) {
        if (item != null) {
            requestItems(List.of(item));
        }
    }

    public boolean isBusy() {
        if (!enabled()) {
            return false;
        }
        return this.phase != Phase.IDLE || !this.pendingNeeded.isEmpty() || !this.queue.isEmpty();
    }

    public boolean shouldPause() {
        return enabled() && isBusy();
    }

    public boolean allowsPlayerMovement() {
        return this.lavaWaitMovement;
    }

    public boolean shouldBlockExternalBreaking() {
        return shouldPause()
                && this.phase != Phase.BREAK
                && this.phase != Phase.WAIT_BREAK
                && this.phase != Phase.BREAK_ENDER
                && this.phase != Phase.WAIT_BREAK_ENDER
                && this.phase != Phase.CLEAR_PLACE
                && this.phase != Phase.WAIT_CLEAR_PLACE;
    }

    public boolean isAllowedBreakTarget(BlockPos pos) {
        if (!shouldPause() || pos == null) {
            return false;
        }
        if (this.phase == Phase.BREAK || this.phase == Phase.WAIT_BREAK
                || this.phase == Phase.CLEAR_PLACE || this.phase == Phase.WAIT_CLEAR_PLACE) {
            return this.placedPos != null && this.placedPos.equals(pos);
        }
        if (this.phase == Phase.BREAK_ENDER || this.phase == Phase.WAIT_BREAK_ENDER) {
            return this.placedEnderPositions.contains(pos);
        }
        return false;
    }

    public void onContainerOpen(int containerId) {
        if (!enabled() || !isBusy()) {
            return;
        }
        if (this.phase == Phase.OPEN
                || this.phase == Phase.WAIT_CONTENT
                || this.phase == Phase.TAKE
                || this.phase == Phase.OPEN_ENDER
                || this.phase == Phase.WAIT_CONTENT_ENDER
                || this.phase == Phase.TAKE_SHULKER) {
            this.expectedContainerId = containerId;
        }
    }

    public void onInventoryContent(int containerId) {
        if (!enabled() || !isBusy()) {
            return;
        }
        if (this.phase != Phase.WAIT_CONTENT
                && this.phase != Phase.TAKE
                && this.phase != Phase.WAIT_CONTENT_ENDER
                && this.phase != Phase.TAKE_SHULKER) {
            return;
        }
        LocalPlayer player = this.client.player;
        if (player == null || this.client.gameMode == null) {
            return;
        }
        if (player.containerMenu == player.inventoryMenu) {
            return;
        }
        if (player.containerMenu.containerId != containerId) {
            return;
        }
        this.expectedContainerId = containerId;
        this.contentReady = true;
        if (this.inventorySnapshot.isEmpty()) {
            this.inventorySnapshot = countNeeded(player);
        }
        if (this.phase == Phase.WAIT_CONTENT) {
            this.phase = Phase.TAKE;
            this.settleTicks = 0;
            this.clickCooldown = CLICK_DELAY;
        } else if (this.phase == Phase.WAIT_CONTENT_ENDER) {
            pruneRejectedEnderSlots(player.containerMenu);
            if (this.enderStashMode || findShulkerInContainer(player.containerMenu) >= 0) {
                this.phase = Phase.TAKE_SHULKER;
                this.settleTicks = 0;
                this.clickCooldown = CLICK_DELAY;
            }
        }
    }

    public boolean shouldSuppressContainerScreen(int containerId) {
        if (!shouldPause()) {
            return false;
        }
        if (this.expectedContainerId >= 0) {
            return containerId == this.expectedContainerId;
        }
        return this.phase == Phase.OPEN
                || this.phase == Phase.WAIT_CONTENT
                || this.phase == Phase.TAKE
                || this.phase == Phase.CLOSE
                || this.phase == Phase.WAIT_CLOSE
                || this.phase == Phase.OPEN_ENDER
                || this.phase == Phase.WAIT_CONTENT_ENDER
                || this.phase == Phase.TAKE_SHULKER
                || this.phase == Phase.CLOSE_ENDER
                || this.phase == Phase.WAIT_CLOSE_ENDER;
    }

    public void tick() {
        if (!enabled()) {
            shutdownIfInactive();
            return;
        }
        LocalPlayer player = this.client.player;
        if (player == null || this.client.level == null || this.client.gameMode == null) {
            if (this.phase != Phase.IDLE || !this.pendingNeeded.isEmpty() || !this.queue.isEmpty()) {
                this.pendingNeeded.clear();
                this.queue.clear();
                abortKeepWorld();
            }
            return;
        }
        scanAutoRefills(player);
        clearRefillLockoutIfWorkResumed();
        if (this.phase == Phase.IDLE) {
            tryStartPending(player);
            if (this.phase == Phase.IDLE) {
                tryStartQueued(player);
            }
            return;
        }
        if (RuntimeAccess.get().currentTick() > this.deadline) {
            failAndStop("global timeout elapsed without completing refill");
            return;
        }
        if (player.isShiftKeyDown()) {
            RuntimeAccess.get().actionBroker().setShift(player, false);
        }
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
        if (shouldBlockExternalBreaking()) {
            stopExternalWork();
        }
        if (this.clickCooldown > 0) {
            this.clickCooldown--;
        }
        switch (this.phase) {
            case EQUIP -> tickEquip(player);
            case CLEAR_PLACE -> tickClearPlace(player);
            case WAIT_CLEAR_PLACE -> tickWaitClearPlace(player);
            case PLACE -> tickPlace(player);
            case SETTLE_PLACE -> tickSettlePlace(player);
            case OPEN -> tickOpen(player);
            case WAIT_CONTENT -> tickWaitContent(player);
            case TAKE -> tickTake(player);
            case CLOSE -> tickClose(player);
            case WAIT_CLOSE -> tickWaitClose(player);
            case POST_CLOSE -> tickPostClose(player);
            case BREAK -> tickBreak(player);
            case WAIT_BREAK -> tickWaitBreak(player);
            case PICKUP -> tickPickup(player);
            case RESTORE -> tickRestore(player);
            case EQUIP_ENDER -> tickEquipEnder(player);
            case PLACE_ENDER -> tickPlaceEnder(player);
            case SETTLE_ENDER -> tickSettleEnder(player);
            case OPEN_ENDER -> tickOpenEnder(player);
            case WAIT_CONTENT_ENDER -> tickWaitContentEnder(player);
            case TAKE_SHULKER -> tickTakeShulker(player);
            case CLOSE_ENDER -> tickCloseEnder(player);
            case WAIT_CLOSE_ENDER -> tickWaitCloseEnder(player);
            case POST_CLOSE_ENDER -> tickPostCloseEnder(player);
            case BREAK_ENDER -> tickBreakEnder(player);
            case WAIT_BREAK_ENDER -> tickWaitBreakEnder(player);
            case PICKUP_ENDER -> tickPickupEnder(player);
            default -> abortKeepWorld();
        }
    }

    public void reset() {
        this.pendingNeeded.clear();
        this.queue.clear();
        abortKeepWorld();
    }

    @Override
    public void onEpochChanged(RuntimeEvent.EpochChanged event) {
        hardClear();
    }

    private void begin(LocalPlayer player, int slot, Set<Item> needed) {
        this.neededItems.clear();
        this.neededItems.addAll(needed);
        this.shulkerInvSlot = slot;
        this.placedPos = null;
        this.enderMode = false;
        this.enderInvSlot = -1;
        this.placedEnderPositions.clear();
        this.enderPlaceIndex = 0;
        this.enderBreakIndex = 0;
        this.placementCommitted = false;
        this.savedLook = new PlayerLook(player.getYRot(), player.getXRot());
        this.wasSneaking = player.isShiftKeyDown();
        if (this.wasSneaking) {
            RuntimeAccess.get().actionBroker().setShift(player, false);
        }
        this.deadline = RuntimeAccess.get().currentTick() + GLOBAL_TIMEOUT_TICKS;
        this.openAttempts = 0;
        this.settleTicks = 0;
        this.clickCooldown = 0;
        this.expectedContainerId = -1;
        this.contentReady = false;
        this.issuedTakeClick = false;
        this.tookItems = false;
        this.takePhaseDone = false;
        if (!this.refillLockedOut) {
            this.refillFailed = false;
        }
        this.inventorySnapshot = Map.of();
        RuntimeAccess.get().actionBroker().cancelQueue();
        RuntimeAccess.get().inventorySwitchGuard().reset();
        stopExternalWork();
        RuntimeAccess.get().actionBroker().tryAcquire(
                LEASE_OWNER,
                EnumSet.of(ResourceLease.CONTAINER, ResourceLease.MAIN_HAND, ResourceLease.INTERACTION),
                0L
        );
        this.phase = Phase.EQUIP;
    }

    private void stopExternalWork() {
        RuntimeAccess.get().interactionUtils().resetRuntime();
        if (this.client.gameMode instanceof MultiPlayerGameModeExtension extension) {
            extension.litematica_printer$resetRuntime();
        }
    }

    private void tickEquip(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (this.clickCooldown > 0) {
            return;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            if (!resolveEquipCarried(player)) {
                if (++this.settleTicks > 40) {
                    failAndStop("equip failed: could not clear cursor item");
                }
                return;
            }
            this.clickCooldown = CLICK_DELAY;
            this.settleTicks = 0;
            return;
        }
        if (isEquippedRefillShulker(player)) {
            this.phase = Phase.PLACE;
            this.settleTicks = 0;
            return;
        }
        int hotbarShulker = findRefillShulkerHotbarSlot(player);
        if (hotbarShulker >= 0) {
            this.shulkerInvSlot = hotbarShulker;
            InventoryUtils.setSelectedSlot(player.getInventory(), hotbarShulker);
            InventoryUtils.syncSelectedHotbarSlot();
            this.clickCooldown = 1;
            if (++this.settleTicks > 30) {
                failAndStop("equip failed: could not select shulker in hotbar");
            }
            return;
        }
        if (!relocateShulkerInvSlot(player)) {
            if (++this.settleTicks > 40) {
                failAndStop("equip failed: inventory slot is not a shulker");
            }
            return;
        }
        ItemStack stack = player.getInventory().getItem(this.shulkerInvSlot);
        if (isToolRefillKind(this.activeKind)) {
            if (countUsableShulkerTools(stack, this.activeKind) <= 0) {
                int alt = findShulkerWithUsableTool(player, this.activeKind);
                if (alt < 0) {
                    failAndStop("equip failed: no shulker with usable tool");
                    return;
                }
                this.shulkerInvSlot = alt;
                stack = player.getInventory().getItem(alt);
                if (!isShulker(stack) || countUsableShulkerTools(stack, this.activeKind) <= 0) {
                    failAndStop("equip failed: alternate tool shulker invalid");
                    return;
                }
            }
        } else if (!containsNeeded(stack, this.neededItems)) {
            if (++this.settleTicks > 40) {
                failAndStop("equip failed: shulker does not contain needed items " + this.neededItems);
            }
            return;
        }
        if (!issueMoveShulkerToHotbar(player, stack)) {
            if (++this.settleTicks > 30) {
                failAndStop("equip failed: could not move shulker to hotbar");
            }
            return;
        }
        this.clickCooldown = CLICK_DELAY;
        this.settleTicks = 0;
    }

    private boolean resolveEquipCarried(LocalPlayer player) {
        AbstractContainerMenu menu = player.inventoryMenu;
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || this.client.gameMode == null) {
            return true;
        }
        if (isShulker(carried)) {
            int hotbar = findEquipHotbarSlot(player);
            if (hotbar < 0) {
                int invEmpty = findEmptyMainInvMenuSlot(menu);
                if (invEmpty < 0) {
                    return false;
                }
                //#if MC > 260100
                this.client.gameMode.handleContainerInput(menu.containerId, invEmpty, 0, ContainerInput.PICKUP, player);
                //#else
                //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, invEmpty, 0, ClickType.PICKUP, player);
                //#endif
                return menu.getCarried().isEmpty();
            }
            int menuSlot = findInventoryMenuSlot(menu, hotbar);
            if (menuSlot < 0) {
                return false;
            }
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, menuSlot, 0, ContainerInput.PICKUP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, ClickType.PICKUP, player);
            //#endif
            this.shulkerInvSlot = hotbar;
            InventoryUtils.setSelectedSlot(player.getInventory(), hotbar);
            InventoryUtils.syncSelectedHotbarSlot();
            if (!menu.getCarried().isEmpty() && !isShulker(menu.getCarried())) {
                int invEmpty = findEmptyMainInvMenuSlot(menu);
                if (invEmpty >= 0) {
                    //#if MC > 260100
                    this.client.gameMode.handleContainerInput(menu.containerId, invEmpty, 0, ContainerInput.PICKUP, player);
                    //#else
                    //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, invEmpty, 0, ClickType.PICKUP, player);
                    //#endif
                }
            }
            return menu.getCarried().isEmpty()
                    || isShulker(player.getInventory().getItem(hotbar))
                    || isEquippedRefillShulker(player);
        }
        int invEmpty = findEmptyMainInvMenuSlot(menu);
        if (invEmpty >= 0) {
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, invEmpty, 0, ContainerInput.PICKUP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, invEmpty, 0, ClickType.PICKUP, player);
            //#endif
            return menu.getCarried().isEmpty();
        }
        return CarriedItemUtils.tryClearCarried(this.client);
    }

    private static int findEmptyMainInvMenuSlot(AbstractContainerMenu menu) {
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 9 || invIndex >= 36) {
                continue;
            }
            if (slot.getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRefillShulkerStack(ItemStack stack) {
        if (!isShulker(stack)) {
            return false;
        }
        if (isToolRefillKind(this.activeKind)) {
            return countUsableShulkerTools(stack, this.activeKind) > 0;
        }
        if (this.neededItems.isEmpty()) {
            return true;
        }
        return containsNeeded(stack, this.neededItems);
    }

    private boolean isEquippedRefillShulker(LocalPlayer player) {
        return isRefillShulkerStack(player.getMainHandItem());
    }

    private int findRefillShulkerHotbarSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (isRefillShulkerStack(inv.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean relocateShulkerInvSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        if (this.shulkerInvSlot >= 0 && this.shulkerInvSlot < 36
                && isRefillShulkerStack(inv.getItem(this.shulkerInvSlot))) {
            return true;
        }
        for (int i = 0; i < 36; i++) {
            if (isRefillShulkerStack(inv.getItem(i))) {
                this.shulkerInvSlot = i;
                return true;
            }
        }
        for (int i = 0; i < 36; i++) {
            if (isShulker(inv.getItem(i))) {
                this.shulkerInvSlot = i;
                return true;
            }
        }
        return false;
    }

    private boolean issueMoveShulkerToHotbar(LocalPlayer player, ItemStack stack) {
        Inventory inv = player.getInventory();
        if (Inventory.isHotbarSlot(this.shulkerInvSlot)) {
            InventoryUtils.setSelectedSlot(inv, this.shulkerInvSlot);
            InventoryUtils.syncSelectedHotbarSlot();
            return true;
        }
        if (!player.inventoryMenu.getCarried().isEmpty()) {
            return false;
        }
        if (this.client.gameMode == null) {
            return false;
        }
        int hotbar = findEquipHotbarSlot(player);
        if (hotbar < 0) {
            return false;
        }
        AbstractContainerMenu menu = player.inventoryMenu;
        int sourceMenuSlot = findInventoryMenuSlot(menu, this.shulkerInvSlot);
        if (sourceMenuSlot < 0) {
            return false;
        }
        ItemStack existing = inv.getItem(hotbar);
        if (existing.isEmpty()) {
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, 0, ClickType.PICKUP, player);
            //#endif
            if (!menu.getCarried().isEmpty()) {
                int dest = findInventoryMenuSlot(menu, hotbar);
                if (dest >= 0) {
                    //#if MC > 260100
                    this.client.gameMode.handleContainerInput(menu.containerId, dest, 0, ContainerInput.PICKUP, player);
                    //#else
                    //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, dest, 0, ClickType.PICKUP, player);
                    //#endif
                }
            }
        } else {
            InventoryUtils.setHotbarSlot(hotbar, inv);
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, hotbar, ContainerInput.SWAP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, hotbar, ClickType.SWAP, player);
            //#endif
        }
        this.shulkerInvSlot = hotbar;
        InventoryUtils.setSelectedSlot(inv, hotbar);
        InventoryUtils.syncSelectedHotbarSlot();
        return true;
    }

    private static int findInventoryMenuSlot(AbstractContainerMenu menu, int invSlot) {
        if (invSlot < 0 || invSlot >= 36) {
            return -1;
        }
        if (menu == null) {
            return -1;
        }
        if (invSlot < 9) {
            int hotbar = 36 + invSlot;
            if (hotbar < menu.slots.size()) {
                Slot slot = menu.slots.get(hotbar);
                if (slot.container instanceof Inventory && slot.getContainerSlot() == invSlot) {
                    return hotbar;
                }
            }
        } else if (invSlot < menu.slots.size()) {
            Slot slot = menu.slots.get(invSlot);
            if (slot.container instanceof Inventory && slot.getContainerSlot() == invSlot) {
                return invSlot;
            }
        }
        int found = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory && slot.getContainerSlot() == invSlot) {
                found = i;
            }
        }
        if (found >= 0) {
            return found;
        }
        return inventorySlotToMenuSlot(menu, invSlot);
    }

    private int findEquipHotbarSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int selected = InventoryUtils.getSelectedSlot(inv);
        java.util.Set<Integer> protectedSlots = protectedHotbarSlots();
        java.util.Set<Integer> pickSet = new java.util.HashSet<>(getPickBlockableHotbarSlots());
        int preferredMaterial = materialHotbarSlotOrAuto();
        if (this.activeKind == RefillKind.MATERIALS && preferredMaterial >= 0) {
            return preferredMaterial;
        }
        boolean materialAuto = preferredMaterial < 0;

        int emptyPick = -1;
        int emptyNonPick = -1;
        int emptyAny = -1;
        int occupiedSafePick = -1;
        int occupiedSafe = -1;
        int occupiedNeededPick = -1;
        int occupiedNeeded = -1;

        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected || protectedSlots.contains(slot)) {
                continue;
            }
            try {
                if (!InventoryUtilsAccessor.canPickToSlot(inv, slot)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            boolean isPick = pickSet.contains(slot);
            ItemStack existing = inv.getItem(slot);
            if (existing.isEmpty()) {
                if (isPick && emptyPick < 0) {
                    emptyPick = slot;
                } else if (!isPick && emptyNonPick < 0) {
                    emptyNonPick = slot;
                }
                if (emptyAny < 0) {
                    emptyAny = slot;
                }
                continue;
            }
            if (isShulker(existing) || isEnderChest(existing)) {
                continue;
            }
            if (isNeeded(existing)) {
                if (isPick && occupiedNeededPick < 0) {
                    occupiedNeededPick = slot;
                } else if (occupiedNeeded < 0) {
                    occupiedNeeded = slot;
                }
                continue;
            }
            if (isPick && occupiedSafePick < 0) {
                occupiedSafePick = slot;
            } else if (occupiedSafe < 0) {
                occupiedSafe = slot;
            }
        }

        if (materialAuto) {
            if (emptyPick >= 0) {
                return emptyPick;
            }
            if (occupiedSafePick >= 0) {
                return occupiedSafePick;
            }
            if (occupiedNeededPick >= 0) {
                return occupiedNeededPick;
            }
        }
        if (emptyNonPick >= 0) {
            return emptyNonPick;
        }
        if (emptyPick >= 0) {
            return emptyPick;
        }
        if (emptyAny >= 0) {
            return emptyAny;
        }
        if (occupiedSafePick >= 0) {
            return occupiedSafePick;
        }
        if (occupiedSafe >= 0) {
            return occupiedSafe;
        }
        if (occupiedNeededPick >= 0) {
            return occupiedNeededPick;
        }
        if (occupiedNeeded >= 0) {
            return occupiedNeeded;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot == selected || protectedSlots.contains(slot)) {
                continue;
            }
            try {
                if (!InventoryUtilsAccessor.canPickToSlot(inv, slot)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            ItemStack existing = inv.getItem(slot);
            if (!isShulker(existing) && !isEnderChest(existing)) {
                return slot;
            }
        }
        return -1;
    }

    private java.util.Set<Integer> protectedHotbarSlots() {
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_PICKAXE.getBooleanValue()) {
            slots.add(toolHotbarSlot(RefillKind.NETHERITE_PICKAXE));
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_SHOVEL.getBooleanValue()) {
            slots.add(toolHotbarSlot(RefillKind.NETHERITE_SHOVEL));
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_AXE.getBooleanValue()) {
            slots.add(toolHotbarSlot(RefillKind.NETHERITE_AXE));
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_HOE.getBooleanValue()) {
            slots.add(toolHotbarSlot(RefillKind.NETHERITE_HOE));
        }
        slots.add(enderHotbarSlot());
        slots.add(gappleHotbarSlot());
        int material = materialHotbarSlotOrAuto();
        if (material >= 0) {
            slots.add(material);
        }
        return slots;
    }

    private void finishClearPlace() {
        InteractionUtils.getRuntime().resetRuntime();
        if (this.clearPreservePos != null) {
            this.placedPos = this.clearPreservePos;
            this.clearPreservePos = null;
        }
        this.phase = this.clearResumePhase;
        this.settleTicks = 0;
    }

    private boolean beginClearAbove(BlockPos containerPos, Phase resumePhase) {
        if (containerPos == null || this.client.level == null) {
            return false;
        }
        BlockPos above = containerPos.above();
        BlockState aboveState = this.client.level.getBlockState(above);
        if (aboveState.isAir()
                || aboveState.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) {
            return false;
        }
        if (aboveState.getDestroySpeed(this.client.level, above) < 0.0F) {
            return false;
        }
        this.clearPreservePos = containerPos;
        this.placedPos = above;
        this.clearResumePhase = resumePhase;
        this.phase = Phase.CLEAR_PLACE;
        this.settleTicks = 0;
        return true;
    }

    private void tickClearPlace(LocalPlayer player) {
        if (this.placedPos == null || this.client.level == null) {
            finishClearPlace();
            return;
        }
        if (this.client.level.getBlockState(this.placedPos).isAir()) {
            finishClearPlace();
            return;
        }
        lookAt(player, this.placedPos);
        if (!prepareBreakTool(player, this.placedPos)) {
            return;
        }
        startBreakShulker(player);
        this.phase = Phase.WAIT_CLEAR_PLACE;
        this.settleTicks = 0;
    }

    private void tickWaitClearPlace(LocalPlayer player) {
        if (this.placedPos == null || this.client.level == null) {
            finishClearPlace();
            return;
        }
        if (this.client.level.getBlockState(this.placedPos).isAir()) {
            finishClearPlace();
            return;
        }
        lookAt(player, this.placedPos);
        continueBreakShulker(player);
        if (++this.settleTicks > 120) {
            if (this.clearPreservePos == null) {
                BlockPos nearby = findNearbyAir(player);
                if (nearby != null) {
                    this.placedPos = nearby;
                }
            }
            finishClearPlace();
        }
    }

    private void tickPlace(LocalPlayer player) {
        if (this.placementCommitted && isRealPlacedShulker()) {
            if (beginClearAbove(this.placedPos, Phase.OPEN)) {
                return;
            }
            selectEmptyHand(player);
            stopExternalWork();
            this.phase = Phase.OPEN;
            this.settleTicks = 0;
            this.openAttempts = 0;
            return;
        }
        if (!isShulker(player.getMainHandItem())) {
            if (!this.placementCommitted && this.settleTicks < 15) {
                this.phase = Phase.EQUIP;
                this.settleTicks = 0;
                return;
            }
            failAndStop("place failed: main hand is not a shulker");
            return;
        }
        if (isLavaNearby(player, LAVA_SAFE_DISTANCE)) {
            enterLavaWait();
            return;
        }
        BlockPos target = blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ());
        BlockState targetState = this.client.level.getBlockState(target);
        if (isLavaBlock(targetState)) {
            enterLavaWait();
            return;
        }
        exitLavaWait();
        if (!isReplaceableForPlace(targetState)) {
            this.placedPos = target;
            if (isRealPlacedShulker()) {
                this.placementCommitted = true;
                this.phase = Phase.SETTLE_PLACE;
                this.settleTicks = 0;
                return;
            }
            this.clearResumePhase = Phase.EQUIP;
            this.phase = Phase.CLEAR_PLACE;
            this.settleTicks = 0;
            return;
        }
        lookAt(player, target);
        BlockHitResult hit = placeHitResult(target);
        InteractionResult result = vanillaUseItemOn(InteractionHand.MAIN_HAND, hit);
        this.placedPos = target;
        if (result.consumesAction() || this.client.level.getBlockState(target).getBlock() instanceof ShulkerBoxBlock) {
            this.placementCommitted = true;
            this.phase = Phase.SETTLE_PLACE;
            this.settleTicks = 0;
        } else if (++this.settleTicks > 20) {
            abortKeepWorld();
        }
    }

    private void tickSettlePlace(LocalPlayer player) {
        if (this.placedPos == null) {
            failAndStop("settle failed: no placed shulker position");
            return;
        }
        if (!isRealPlacedShulker()) {
            if (++this.settleTicks > PLACE_CONFIRM_TICKS) {
                BlockPos found = findNearbyPlacedShulker(player);
                if (found != null) {
                    this.placedPos = found;
                    this.placementCommitted = true;
                    this.settleTicks = 0;
                    return;
                }
                abortKeepWorld();
            }
            return;
        }
        this.placementCommitted = true;
        if (++this.settleTicks < PLACE_SETTLE_TICKS) {
            return;
        }
        if (beginClearAbove(this.placedPos, Phase.OPEN)) {
            return;
        }
        selectEmptyHand(player);
        stopExternalWork();
        this.phase = Phase.OPEN;
        this.settleTicks = 0;
        this.openAttempts = 0;
    }

    private void tickOpen(LocalPlayer player) {
        if (this.placedPos == null) {
            failAndStop("settle failed: no placed shulker position");
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            this.expectedContainerId = player.containerMenu.containerId;
            this.phase = Phase.WAIT_CONTENT;
            this.settleTicks = 0;
            return;
        }
        if (!isRealPlacedShulker()) {
            BlockPos found = findNearbyPlacedShulker(player);
            if (found == null) {
                failAndStop("open failed: placed shulker not found in world");
                return;
            }
            this.placedPos = found;
            this.placementCommitted = true;
        }
        if (beginClearAbove(this.placedPos, Phase.OPEN)) {
            return;
        }
        selectEmptyHand(player);
        stopExternalWork();
        lookAt(player, this.placedPos);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(this.placedPos),
                Direction.UP,
                this.placedPos,
                false
        );
        InteractionResult result = vanillaUseItemOn(InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction() && player.containerMenu == player.inventoryMenu) {
            result = vanillaUseItemOn(InteractionHand.OFF_HAND, hit);
        }
        this.openAttempts++;
        this.phase = Phase.WAIT_CONTENT;
        this.settleTicks = 0;
        this.contentReady = false;
    }

    private void tickWaitContent(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            if (this.expectedContainerId < 0) {
                this.expectedContainerId = player.containerMenu.containerId;
            }
            if (this.contentReady || hasNeededInContainer(player.containerMenu)) {
                if (this.inventorySnapshot.isEmpty()) {
                    this.inventorySnapshot = countNeeded(player);
                }
                this.contentReady = true;
                this.phase = Phase.TAKE;
                this.settleTicks = 0;
                this.clickCooldown = CLICK_DELAY;
                return;
            }
            if (++this.settleTicks > CONTENT_WAIT_TICKS) {
                AbstractContainerMenu menu = player.containerMenu;
                failAndStop("shulker open but no matching items (containerSize=" + containerSize(menu)
                        + " kind=" + this.activeKind
                        + " needed=" + this.neededItems + ")");
            }
            return;
        }
        if (++this.settleTicks > 20) {
            retryOpenOrAbort(player);
        }
    }

    private void retryOpenOrAbort(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        this.contentReady = false;
        this.expectedContainerId = -1;
        failAndStop("could not open placed shulker after " + this.openAttempts + " attempts");
    }

    private void tickTake(LocalPlayer player) {
        if (player.containerMenu == player.inventoryMenu) {
            refreshTookFlag(player);
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
            return;
        }
        if (this.clickCooldown > 0) {
            return;
        }
        if (isToolRefillKind(this.activeKind)) {
            tickTakePickaxeSwap(player);
            return;
        }
        if (this.activeKind == RefillKind.ENDER_CHEST_STACK
                || this.activeKind == RefillKind.ENCHANTED_GOLDEN_APPLE
                || (this.activeKind == RefillKind.MATERIALS && this.targetInvSlot >= 0)) {
            tickTakeLimited(player);
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) {
            if (countEmptySlots(player) <= 1) {
                int dest = findPartialMergeMenuSlot(menu, menu.getCarried());
                if (dest >= 0) {
                    //#if MC > 260100
                    this.client.gameMode.handleContainerInput(menu.containerId, dest, 0, ContainerInput.PICKUP, player);
                    //#else
                    //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, dest, 0, ClickType.PICKUP, player);
                    //#endif
                    this.issuedTakeClick = true;
                    this.clickCooldown = CLICK_DELAY;
                    refreshTookFlag(player);
                    return;
                }
            }
            if (!returnCarriedToContainer(player, menu)) {
                failAndStop("could not return carried items to shulker after material take");
            }
            return;
        }
        expandNeededFromContainer(player, menu);
        int slot = findNextTakeableSlot(player, menu);
        if (slot < 0) {
            refreshTookFlag(player);
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
            return;
        }
        ItemStack source = menu.slots.get(slot).getItem();
        int empty = countEmptySlots(player);
        if (empty < 1 && !canMergeIntoPartialStacks(player, source)) {
            failAndStop("inventory full while refilling materials; no empty or merge slot for "
                    + source.getItem());
            return;
        }
        if (empty > 1) {
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, player);
            //#endif
        } else {
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
            //#endif
        }
        this.issuedTakeClick = true;
        this.clickCooldown = CLICK_DELAY;
        refreshTookFlag(player);
        if (!menu.getCarried().isEmpty() && empty > 1) {
            if (!returnCarriedToContainer(player, menu)) {
                failAndStop("carried remainder after material take could not be returned to shulker");
            }
        }
    }

    private void tickTakeLimited(LocalPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (this.takeRemaining <= 0) {
            if (!menu.getCarried().isEmpty()) {
                returnCarriedToContainer(player, menu);
                this.clickCooldown = CLICK_DELAY;
                return;
            }
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            int dest = resolveLimitedDepositMenuSlot(menu, player);
            if (dest < 0) {
                if (!returnCarriedToContainer(player, menu)) {
                    this.takePhaseDone = true;
                    this.phase = Phase.CLOSE;
                    this.settleTicks = 0;
                }
                return;
            }
            ItemStack carried = menu.getCarried();
            ItemStack destStack = menu.slots.get(dest).getItem();
            int space = destStack.isEmpty()
                    ? Math.min(carried.getMaxStackSize(), this.takeRemaining)
                    : Math.min(destStack.getMaxStackSize() - destStack.getCount(), this.takeRemaining);
            if (space <= 0) {
                if (!returnCarriedToContainer(player, menu)) {
                    this.takePhaseDone = true;
                    this.phase = Phase.CLOSE;
                    this.settleTicks = 0;
                }
                return;
            }
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, dest, 0, ContainerInput.PICKUP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, dest, 0, ClickType.PICKUP, player);
            //#endif
            this.issuedTakeClick = true;
            this.tookItems = true;
            this.takeRemaining = Math.max(0, this.takeRemaining - space);
            this.clickCooldown = CLICK_DELAY;
            if (this.takeRemaining <= 0) {
                this.takePhaseDone = true;
            }
            return;
        }
        int slot = findNextTakeableSlot(player, menu);
        if (slot < 0) {
            if (!this.tookItems && this.takeRemaining > 0) {
                failAndStop("opened shulker had no matching items for " + this.activeKind
                        + " (still need " + this.takeRemaining + ")");
                return;
            }
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
            return;
        }
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
        //#endif
        this.issuedTakeClick = true;
        this.clickCooldown = CLICK_DELAY;
    }

    private int resolveLimitedDepositMenuSlot(AbstractContainerMenu menu, LocalPlayer player) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return -1;
        }
        if (this.targetInvSlot >= 0) {
            int mapped = inventorySlotToMenuSlot(menu, this.targetInvSlot);
            if (mapped >= 0 && mapped < menu.slots.size()) {
                ItemStack stack = menu.slots.get(mapped).getItem();
                if (stack.isEmpty() || (ItemStack.isSameItemSameComponents(stack, carried)
                        && stack.getCount() < stack.getMaxStackSize())) {
                    return mapped;
                }
            }
        }
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 9) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < stack.getMaxStackSize()) {
                return i;
            }
        }
        return -1;
    }

    private void tickTakePickaxeSwap(LocalPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (this.pickaxeSwapStage >= 3) {
            if (!menu.getCarried().isEmpty()) {
                depositCarriedIntoContainer(player, menu);
                this.clickCooldown = CLICK_DELAY;
                return;
            }
            this.pickaxeSwapDone = true;
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            handlePickaxeSwapCarried(player, menu);
            return;
        }
        if (this.pickaxeSwapStage == 0) {
            int goodSlot = findBestPickaxeInContainer(menu);
            if (goodSlot < 0) {
                this.stashWornPickaxeShulker = true;
                this.stashShulkerInvSlot = -1;
                this.invShulkerSlotsSnapshot.clear();
                Inventory inv = player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (isShulker(inv.getItem(i))) {
                        this.invShulkerSlotsSnapshot.add(i);
                    }
                }
                this.takePhaseDone = true;
                this.phase = Phase.CLOSE;
                this.settleTicks = 0;
                return;
            }
            this.targetInvSlot = activeToolHotbarSlot();
            clickPickup(menu, player, goodSlot);
            this.pickaxeContainerSlot = goodSlot;
            this.pickaxeSwapStage = 1;
            return;
        }
        this.pickaxeSwapStage = 3;
    }

    private void handlePickaxeSwapCarried(LocalPlayer player, AbstractContainerMenu menu) {
        switch (this.pickaxeSwapStage) {
            case 1 -> {
                int dest = resolveTargetPickaxeMenuSlot(menu, player);
                if (dest < 0) {
                    dest = inventorySlotToMenuSlot(menu, activeToolHotbarSlot());
                    this.targetInvSlot = activeToolHotbarSlot();
                }
                if (dest < 0 || dest >= menu.slots.size()) {
                    failAndStop("could not map hotbar tool slot " + activeToolHotbarSlot() + " in open menu");
                    return;
                }
                ItemStack destStack = menu.slots.get(dest).getItem();
                if (!destStack.isEmpty() && !isMatchingTool(destStack, this.activeKind)) {
                    if (!tryDepositHotbarStackToContainer(player, menu, dest)) {
                        failAndStop("hotbar tool slot " + activeToolHotbarSlot() + " blocked and cannot deposit into shulker");
                        return;
                    }
                    this.clickCooldown = CLICK_DELAY;
                    return;
                }
                clickPickup(menu, player, dest);
                this.tookItems = true;
                this.issuedTakeClick = true;
                this.pickaxeSwapStage = 2;
            }
            case 2 -> {
                if (menu.getCarried().isEmpty()) {
                    this.pickaxeSwapStage = 3;
                    return;
                }
                int cont = -1;
                if (this.pickaxeContainerSlot >= 0
                        && this.pickaxeContainerSlot < menu.slots.size()
                        && menu.slots.get(this.pickaxeContainerSlot).getItem().isEmpty()) {
                    cont = this.pickaxeContainerSlot;
                }
                if (cont < 0) {
                    cont = findEmptyContainerSlot(menu);
                }
                if (cont < 0) {
                    depositCarriedIntoContainer(player, menu);
                } else {
                    clickPickup(menu, player, cont);
                }
                this.issuedTakeClick = true;
                this.pickaxeSwapStage = 3;
            }
            default -> {
                depositCarriedIntoContainer(player, menu);
                this.pickaxeSwapStage = 3;
            }
        }
        this.clickCooldown = CLICK_DELAY;
    }

    private void clickPickup(AbstractContainerMenu menu, LocalPlayer player, int menuSlot) {
        if (this.client.gameMode == null || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return;
        }
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, menuSlot, 0, ContainerInput.PICKUP, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 0, ClickType.PICKUP, player);
        //#endif
        this.issuedTakeClick = true;
        this.clickCooldown = CLICK_DELAY;
    }

    private boolean makePickaxeInventorySpace(LocalPlayer player, AbstractContainerMenu menu) {
        int menuSlot = inventorySlotToMenuSlot(menu, activeToolHotbarSlot());
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return false;
        }
        ItemStack stack = menu.slots.get(menuSlot).getItem();
        if (stack.isEmpty()) {
            return true;
        }
        if (isShulker(stack)) {
            return false;
        }
        return tryDepositHotbarStackToContainer(player, menu, menuSlot);
    }

    private static int findEmptyMainInventoryMenuSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 9 || invIndex >= 36) {
                continue;
            }
            if (slot.getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static int findEmptyHotbarMenuSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 9) {
                continue;
            }
            if (slot.getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int findUsablePickaxeMainInventoryMenuSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 9 || invIndex >= 36) {
                continue;
            }
            if (isUsableTool(slot.getItem(), this.activeKind)) {
                return i;
            }
        }
        return -1;
    }

    private int resolveTargetPickaxeMenuSlot(AbstractContainerMenu menu, LocalPlayer player) {
        int mapped = inventorySlotToMenuSlot(menu, this.targetInvSlot);
        if (mapped >= 0) {
            ItemStack stack = menu.slots.get(mapped).getItem();
            if (isToolAtOrBelowMinDurability(stack, this.activeKind)) {
                return mapped;
            }
        }
        int size = containerSize(menu);
        int best = -1;
        float bestRemaining = 1.0f;
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 9) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!isToolAtOrBelowMinDurability(stack, this.activeKind)) {
                continue;
            }
            float remaining = remainingDurabilityPercent(stack);
            if (remaining < bestRemaining) {
                bestRemaining = remaining;
                best = i;
                this.targetInvSlot = invIndex;
            }
        }
        return best;
    }

    private int findBestPickaxeInContainer(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        int bestSlot = -1;
        float bestRemaining = Float.MAX_VALUE;
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!isUsableTool(stack, this.activeKind)) {
                continue;
            }
            float remaining = remainingDurabilityPercent(stack);
            if (remaining < bestRemaining) {
                bestRemaining = remaining;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void depositCarriedIntoContainer(LocalPlayer player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || this.client.gameMode == null) {
            return;
        }
        int size = containerSize(menu);
        int empty = -1;
        int merge = -1;
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                if (empty < 0) {
                    empty = i;
                }
            } else if (ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < stack.getMaxStackSize()) {
                merge = i;
                break;
            }
        }
        int target = merge >= 0 ? merge : empty;
        if (target < 0) {
            return;
        }
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, target, 0, ContainerInput.PICKUP, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, target, 0, ClickType.PICKUP, player);
        //#endif
    }

    private static int inventorySlotToMenuSlot(AbstractContainerMenu menu, int invSlot) {
        if (invSlot < 0 || invSlot >= 36) {
            return -1;
        }
        int containerSlots = containerSize(menu);
        if (invSlot < 9) {
            int hotbar = containerSlots + 27 + invSlot;
            if (hotbar < menu.slots.size()) {
                return hotbar;
            }
        } else {
            int main = containerSlots + (invSlot - 9);
            if (main < menu.slots.size()) {
                return main;
            }
        }
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory && slot.getContainerSlot() == invSlot) {
                return i;
            }
        }
        return -1;
    }

    private void tickClose(LocalPlayer player) {
        refreshTookFlag(player);
        this.takePhaseDone = true;
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        this.phase = Phase.WAIT_CLOSE;
        this.settleTicks = 0;
    }

    private void tickWaitClose(LocalPlayer player) {
        refreshTookFlag(player);
        if (player.containerMenu != player.inventoryMenu) {
            if (++this.settleTicks % 8 == 0) {
                player.closeContainer();
            }
            if (this.settleTicks > CLOSE_WAIT_TICKS) {
                this.phase = Phase.POST_CLOSE;
                this.settleTicks = 0;
            }
            return;
        }
        this.phase = Phase.POST_CLOSE;
        this.settleTicks = 0;
    }

    private void tickPostClose(LocalPlayer player) {
        refreshTookFlag(player);
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (++this.settleTicks < POST_CLOSE_TICKS) {
            return;
        }
        if (canBreakPlacedShulker()) {
            if (countEmptySlots(player) < 1) {
                if (!ensureInventorySpaceClosed(player, 1)) {
                    finishWithoutBreak(player);
                    return;
                }
                this.settleTicks = POST_CLOSE_TICKS;
                return;
            }
            this.phase = Phase.BREAK;
            this.settleTicks = 0;
        } else {
            finishWithoutBreak(player);
        }
    }

    private boolean canBreakPlacedShulker() {
        if (!this.takePhaseDone || this.placedPos == null || this.client.level == null) {
            return false;
        }
        LocalPlayer player = this.client.player;
        if (player == null || player.containerMenu != player.inventoryMenu) {
            return false;
        }
        return isRealPlacedShulker();
    }

    private void tickBreak(LocalPlayer player) {
        if (!canBreakPlacedShulker()) {
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer();
                this.phase = Phase.WAIT_CLOSE;
                this.settleTicks = 0;
                return;
            }
            finishWithoutBreak(player);
            return;
        }
        lookAt(player, this.placedPos);
        startBreakShulker(player);
        this.phase = Phase.WAIT_BREAK;
        this.settleTicks = 0;
    }

    private void tickWaitBreak(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (this.placedPos == null || this.client.level.getBlockState(this.placedPos).isAir()) {
            this.phase = Phase.PICKUP;
            this.settleTicks = 0;
            return;
        }
        if (!(this.client.level.getBlockState(this.placedPos).getBlock() instanceof ShulkerBoxBlock)) {
            this.phase = Phase.PICKUP;
            this.settleTicks = 0;
            return;
        }
        if (!canBreakPlacedShulker()) {
            if (this.stashWornPickaxeShulker) {
                this.phase = Phase.PICKUP;
                this.settleTicks = 0;
                return;
            }
            finishWithoutBreak(player);
            return;
        }
        lookAt(player, this.placedPos);
        continueBreakShulker(player);
        if (++this.settleTicks > 120) {
            this.phase = this.stashWornPickaxeShulker ? Phase.PICKUP : Phase.RESTORE;
            this.settleTicks = 0;
        }
    }

    private boolean prepareBreakTool(LocalPlayer player, BlockPos pos) {
        if (pos == null || this.client.level == null || this.client.gameMode == null) {
            return false;
        }
        RuntimeAccess.get().inventorySwitchGuard().reset();
        BlockState state = this.client.level.getBlockState(pos);
        if (state.isAir()) {
            return true;
        }
        Direction face = Direction.DOWN;
        InteractionUtils.getRuntime().continueDestroyBlockForMine(pos, face, true);
        if (RuntimeAccess.get().inventorySwitchGuard().isWaiting()) {
            return false;
        }
        ItemStack hand = player.getMainHandItem();
        boolean handIsBlock = !hand.isEmpty() && hand.getItem() instanceof net.minecraft.world.item.BlockItem;
        boolean needsTool = hand.isEmpty()
                || handIsBlock
                || isShulker(hand)
                || isEnderChest(hand)
                || state.requiresCorrectToolForDrops()
                || !hand.isCorrectToolForDrops(state);
        if (needsTool) {
            InventoryUtils.switchToBestTool(player, state, pos);
            if (RuntimeAccess.get().inventorySwitchGuard().isWaiting()) {
                return false;
            }
            hand = player.getMainHandItem();
            if (hand.isEmpty()
                    || hand.getItem() instanceof net.minecraft.world.item.BlockItem
                    || isShulker(hand)
                    || isEnderChest(hand)) {
                InventoryUtils.switchToBestTool(player, state, pos);
                return false;
            }
        }
        return true;
    }

    private void startBreakShulker(LocalPlayer player) {
        BlockPos pos = this.placedPos;
        if (pos == null || this.client.gameMode == null) {
            return;
        }
        Direction face = Direction.DOWN;
        if (!prepareBreakTool(player, pos)) {
            return;
        }
        InteractionUtils.getRuntime().continueDestroyBlockForMine(pos, face, true);
        this.client.gameMode.startDestroyBlock(pos, face);
        player.swing(InteractionHand.MAIN_HAND);
        InteractionUtils.getRuntime().continueDestroyBlockForMine(pos, face, true);
    }

    private void continueBreakShulker(LocalPlayer player) {
        BlockPos pos = this.placedPos;
        if (pos == null || this.client.gameMode == null) {
            return;
        }
        Direction face = Direction.DOWN;
        if (!prepareBreakTool(player, pos)) {
            return;
        }
        InteractionUtils.getRuntime().continueDestroyBlockForMine(pos, face, true);
        this.client.gameMode.continueDestroyBlock(pos, face);
        player.swing(InteractionHand.MAIN_HAND);
        if (this.settleTicks > 0 && this.settleTicks % 20 == 0) {
            //#if MC >= 11900
            NetworkUtils.sendPacket(sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    pos,
                    face,
                    sequence
            ));
            NetworkUtils.sendPacket(sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    pos,
                    face,
                    sequence
            ));
            //#else
            //$$ NetworkUtils.sendPacket(new ServerboundPlayerActionPacket(
            //$$         ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
            //$$         pos,
            //$$         face
            //$$ ));
            //$$ NetworkUtils.sendPacket(new ServerboundPlayerActionPacket(
            //$$         ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
            //$$         pos,
            //$$         face
            //$$ ));
            //#endif
        }
    }

    private void tickPickup(LocalPlayer player) {
        if (this.refillFailed || this.refillLockedOut) {
            finalizeFailedRefill();
            return;
        }
        if (this.stashWornPickaxeShulker) {
            tickWaitStashShulker(player);
            return;
        }
        if (++this.settleTicks < POST_BREAK_MOVEMENT_LOCK_TICKS) {
            return;
        }
        this.phase = Phase.RESTORE;
        this.settleTicks = 0;
    }

    private void tickWaitStashShulker(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        int slot = findStashablePickaxeShulker(player);
        if (slot < 0) {
            if (++this.settleTicks > 120) {
                this.stashWornPickaxeShulker = false;
                this.stashShulkerInvSlot = -1;
                this.enderStashMode = false;
                this.phase = Phase.RESTORE;
                this.settleTicks = 0;
            }
            return;
        }
        if (findEnderChestSlot(player) < 0) {
            this.stashWornPickaxeShulker = false;
            this.stashShulkerInvSlot = -1;
            this.enderStashMode = false;
            this.phase = Phase.RESTORE;
            this.settleTicks = 0;
            return;
        }
        this.stashWornPickaxeShulker = false;
        this.stashShulkerInvSlot = slot;
        this.enderStashMode = true;
        this.settleTicks = 0;
        beginEnder(player, Set.of(toolItem(this.activeKind)));
        if (this.phase != Phase.EQUIP_ENDER) {
            this.enderStashMode = false;
            this.stashShulkerInvSlot = -1;
            this.phase = Phase.RESTORE;
            this.settleTicks = 0;
        }
    }

    private int findStashablePickaxeShulker(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int newSlot = -1;
        int wornSlot = -1;
        int anySlot = -1;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack) || !shulkerContainsTool(stack, refillKindForShulkerSearch())) {
                continue;
            }
            if (anySlot < 0) {
                anySlot = slot;
            }
            if (!shulkerHasUsableTool(stack, refillKindForShulkerSearch()) && wornSlot < 0) {
                wornSlot = slot;
            }
            if (!this.invShulkerSlotsSnapshot.contains(slot) && newSlot < 0) {
                newSlot = slot;
            }
        }
        if (newSlot >= 0) {
            return newSlot;
        }
        if (wornSlot >= 0) {
            return wornSlot;
        }
        return anySlot;
    }

    private int findWornOnlyPickaxeShulker(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack)) {
                continue;
            }
            if (!shulkerContainsTool(stack, refillKindForShulkerSearch())) {
                continue;
            }
            if (!shulkerHasUsableTool(stack, refillKindForShulkerSearch())) {
                return slot;
            }
        }
        return -1;
    }

    private int findAnyNetheritePickaxeShulker(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack)) {
                continue;
            }
            if (shulkerContainsTool(stack, refillKindForShulkerSearch())) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean shulkerContainsTool(ItemStack shulker, RefillKind kind) {
        for (ItemStack inner : listShulkerContents(shulker)) {
            if (isMatchingTool(inner, kind)) {
                return true;
            }
        }
        return false;
    }

    private void tickRestore(LocalPlayer player) {
        if (this.refillFailed || this.refillLockedOut) {
            finalizeFailedRefill();
            return;
        }
        restoreLook(player);
        RuntimeAccess.get().actionBroker().releaseOwner(LEASE_OWNER);
        stopExternalWork();
        hardClear();
    }

    private void finishWithoutBreak(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        restoreLook(player);
        RuntimeAccess.get().actionBroker().releaseOwner(LEASE_OWNER);
        stopExternalWork();
        hardClear();
    }

    private void abortKeepWorld() {
        LocalPlayer player = this.client.player;
        if (player != null && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        if (player != null) {
            restoreLook(player);
        } else {
            NetworkUtils.clearScopedLookOverride();
        }
        RuntimeAccess.get().actionBroker().releaseOwner(LEASE_OWNER);
        stopExternalWork();
        hardClear();
    }

    private void clearRefillLockoutIfWorkResumed() {
        if (!this.refillLockedOut) {
            return;
        }
        if (Configs.Core.WORK_SWITCH.getBooleanValue()) {
            this.refillLockedOut = false;
            this.refillFailed = false;
            if (this.enderFetchCooldownUntilTick == Long.MAX_VALUE) {
                this.enderFetchCooldownUntilTick = 0L;
            }
        }
    }

    private void failAndStop(String reason) {
        if (this.refillLockedOut) {
            finalizeFailedRefill();
            return;
        }
        this.refillFailed = true;
        this.refillLockedOut = true;
        this.enderFetchCooldownUntilTick = Long.MAX_VALUE;
        this.queue.clear();
        this.pendingNeeded.clear();
        this.rejectedEnderPickaxeSlots.clear();
        String detail = reason == null || reason.isEmpty() ? "unknown" : reason;
        String kind = this.activeKind == null ? "NONE" : this.activeKind.name();
        String phaseName = this.phase == null ? "NONE" : this.phase.name();
        String msg = "[Printer Refill] FAILED kind=" + kind
                + " phase=" + phaseName
                + " ender=" + this.enderMode
                + " took=" + this.tookItems
                + " opens=" + this.openAttempts
                + " | " + detail;
        MessageUtils.addMessage(msg);
        MessageUtils.setOverlayMessage("[Printer Refill] " + detail);
        if (Configs.Core.WORK_SWITCH.getBooleanValue()) {
            Configs.Core.WORK_SWITCH.setBooleanValue(false);
            MessageUtils.addMessage("[Printer Refill] Work switch disabled after failure");
        }
        LocalPlayer p = this.client.player;
        if (p != null && p.containerMenu != p.inventoryMenu) {
            p.closeContainer();
        }
        if (this.enderMode && !this.placedEnderPositions.isEmpty()) {
            this.takePhaseDone = true;
            this.phase = Phase.BREAK_ENDER;
            this.settleTicks = 0;
            this.enderBreakIndex = 0;
            this.openAttempts = 0;
            this.contentReady = false;
            this.expectedContainerId = -1;
            return;
        }
        if (this.placementCommitted && this.placedPos != null) {
            this.takePhaseDone = true;
            this.phase = Phase.BREAK;
            this.settleTicks = 0;
            this.openAttempts = 0;
            this.contentReady = false;
            this.expectedContainerId = -1;
            return;
        }
        finalizeFailedRefill();
    }

    private void finalizeFailedRefill() {
        this.queue.clear();
        this.pendingNeeded.clear();
        this.rejectedEnderPickaxeSlots.clear();
        this.enderFetchActive = false;
        this.enderFetchCooldownUntilTick = Long.MAX_VALUE;
        LocalPlayer player = this.client.player;
        if (player != null && player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        if (player != null) {
            restoreLook(player);
        } else {
            NetworkUtils.clearScopedLookOverride();
        }
        RuntimeAccess.get().actionBroker().releaseOwner(LEASE_OWNER);
        stopExternalWork();
        NetworkUtils.clearScopedLookOverride();
        this.phase = Phase.IDLE;
        this.wasSneaking = false;
        this.refillFailed = true;
        this.neededItems.clear();
        this.shulkerInvSlot = -1;
        this.placedPos = null;
        this.savedLook = null;
        this.deadline = 0;
        this.openAttempts = 0;
        this.settleTicks = 0;
        this.clickCooldown = 0;
        this.expectedContainerId = -1;
        this.contentReady = false;
        this.issuedTakeClick = false;
        this.tookItems = false;
        this.takePhaseDone = false;
        this.inventorySnapshot = Map.of();
        this.enderMode = false;
        this.enderInvSlot = -1;
        this.placedEnderPositions.clear();
        this.enderPlaceIndex = 0;
        this.enderBreakIndex = 0;
        this.placementCommitted = false;
        this.activeKind = RefillKind.MATERIALS;
        this.targetInvSlot = -1;
        this.takeRemaining = 0;
        this.pickaxeSwapDone = false;
        this.pickaxeSwapStage = 0;
        this.pickaxeTempInvSlot = -1;
        this.pickaxeContainerSlot = -1;
        this.enderFetchActive = false;
        this.enderFetchKind = RefillKind.MATERIALS;
        this.enderFetchTargetSlot = -1;
        this.enderFetchTakeRemaining = 0;
        this.stashWornPickaxeShulker = false;
        this.enderStashMode = false;
        this.stashShulkerInvSlot = -1;
        this.invShulkerSlotsSnapshot.clear();
    }

    private void restoreLook(LocalPlayer player) {
        NetworkUtils.clearScopedLookOverride();
        if (this.wasSneaking && !player.isShiftKeyDown()) {
            RuntimeAccess.get().actionBroker().setShift(player, true);
        }
        this.wasSneaking = false;
    }

    private void hardClear() {
        NetworkUtils.clearScopedLookOverride();
        this.phase = Phase.IDLE;
        this.wasSneaking = false;
        if (!this.refillLockedOut) {
            this.refillFailed = false;
        }
        this.neededItems.clear();
        this.shulkerInvSlot = -1;
        this.placedPos = null;
        this.clearPreservePos = null;
        this.clearResumePhase = Phase.EQUIP;
        this.lavaWaitMovement = false;
        this.savedLook = null;
        this.deadline = 0;
        this.openAttempts = 0;
        this.settleTicks = 0;
        this.clickCooldown = 0;
        this.expectedContainerId = -1;
        this.contentReady = false;
        this.issuedTakeClick = false;
        this.tookItems = false;
        this.takePhaseDone = false;
        this.inventorySnapshot = Map.of();
        this.enderMode = false;
        this.enderInvSlot = -1;
        this.placedEnderPositions.clear();
        this.enderPlaceIndex = 0;
        this.enderBreakIndex = 0;
        this.placementCommitted = false;
        this.activeKind = RefillKind.MATERIALS;
        this.targetInvSlot = -1;
        this.takeRemaining = 0;
        this.pickaxeSwapDone = false;
        this.pickaxeSwapStage = 0;
        this.pickaxeTempInvSlot = -1;
        this.pickaxeContainerSlot = -1;
        this.enderFetchActive = false;
        this.enderFetchKind = RefillKind.MATERIALS;
        this.enderFetchTargetSlot = -1;
        this.enderFetchTakeRemaining = 0;
        this.stashWornPickaxeShulker = false;
        this.enderStashMode = false;
        this.stashShulkerInvSlot = -1;
        this.invShulkerSlotsSnapshot.clear();
    }

    private void hardClearAll() {
        this.pendingNeeded.clear();
        this.queue.clear();
        this.rejectedEnderPickaxeSlots.clear();
        hardClear();
    }

    private void refreshTookFlag(LocalPlayer player) {
        if (this.tookItems || !this.issuedTakeClick || this.inventorySnapshot.isEmpty()) {
            return;
        }
        Map<Item, Integer> now = countNeeded(player);
        for (Item item : this.neededItems) {
            if (now.getOrDefault(item, 0) > this.inventorySnapshot.getOrDefault(item, 0)) {
                this.tookItems = true;
                return;
            }
        }
    }

    private Map<Item, Integer> countNeeded(LocalPlayer player) {
        Map<Item, Integer> counts = new HashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && isNeeded(stack)) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
        return counts;
    }

    private int findNextNeededSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty() || !isNeeded(stack)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private int findNextTakeableSlot(LocalPlayer player, AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty() || !isNeeded(stack)) {
                continue;
            }
            if (!canTakeStack(player, stack)) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static int containerSize(AbstractContainerMenu menu) {
        if (menu.slots.isEmpty()) {
            return 0;
        }
        int nonPlayer = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) {
                break;
            }
            nonPlayer++;
        }
        if (nonPlayer > 0) {
            return nonPlayer;
        }
        int total = menu.slots.size();
        if (total > 36) {
            return total - 36;
        }
        try {
            return Math.min(total, menu.slots.get(0).container.getContainerSize());
        } catch (Exception ignored) {
            return total;
        }
    }


    private static List<Slot> nonPlayerSlots(AbstractContainerMenu menu) {
        List<Slot> out = new java.util.ArrayList<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) {
                out.add(slot);
            }
        }
        return out;
    }

    private boolean hasNeededInContainer(AbstractContainerMenu menu) {
        return findNextNeededSlot(menu) >= 0;
    }

    private void lookAt(LocalPlayer player, BlockPos pos) {
        Vec3 eyes = player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(pos);
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        PlayerLook look = new PlayerLook(yaw, pitch);
        NetworkUtils.setScopedLookOverride(look);
        NetworkUtils.sendLookPacketIgnoringQueuedLook(player, look);
    }

    @Nullable
    private BlockPos findNearbyAir(LocalPlayer player) {
        for (int dy = 1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = blockPosAt(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
                    if (isReplaceableForPlace(this.client.level.getBlockState(pos))
                            && !isLavaNearbyPos(pos, LAVA_SAFE_DISTANCE)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private BlockHitResult placeHitResult(BlockPos target) {
        BlockPos below = target.below();
        BlockState belowState = this.client.level.getBlockState(below);
        if (isSolidPlaceSupport(belowState)) {
            return new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false);
        }
        return new BlockHitResult(Vec3.atCenterOf(target), Direction.DOWN, target, false);
    }

    private static boolean isReplaceableForPlace(BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (isLavaBlock(state)) {
            return false;
        }
        if (isWaterBlock(state)) {
            return true;
        }
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static boolean isWaterBlock(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock
                && !isLavaBlock(state)) {
            return true;
        }
        return !state.getFluidState().isEmpty()
                && state.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER);
    }

    private static boolean isLavaBlock(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.is(net.minecraft.world.level.block.Blocks.LAVA)) {
            return true;
        }
        return !state.getFluidState().isEmpty()
                && state.getFluidState().is(net.minecraft.world.level.material.Fluids.LAVA);
    }

    private static boolean isSolidPlaceSupport(BlockState state) {
        return !state.isAir()
                && !isWaterBlock(state)
                && !isLavaBlock(state)
                && !state.canBeReplaced();
    }

    private void enterLavaWait() {
        if (!this.lavaWaitMovement) {
            this.lavaWaitMovement = true;
            NetworkUtils.clearScopedLookOverride();
        }
    }

    private void exitLavaWait() {
        this.lavaWaitMovement = false;
    }

    private boolean isLavaNearby(LocalPlayer player, int distance) {
        if (player == null || this.client.level == null) {
            return false;
        }
        return isLavaNearbyPos(player.blockPosition(), distance)
                || isLavaNearbyPos(blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ()), distance);
    }

    private boolean isLavaNearbyPos(BlockPos origin, int distance) {
        if (origin == null || this.client.level == null) {
            return false;
        }
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dy = -distance; dy <= distance; dy++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (isLavaBlock(this.client.level.getBlockState(origin.offset(dx, dy, dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isNeeded(ItemStack stack) {
        if (stack.isEmpty() || this.neededItems.isEmpty()) {
            return false;
        }
        Item stackItem = stack.getItem();
        for (Item item : this.neededItems) {
            if (item != null && (stackItem == item || stack.is(item))) {
                return true;
            }
        }
        return false;
    }

    private void expandNeededFromContainer(LocalPlayer player, AbstractContainerMenu menu) {
        int threshold = materialThreshold();
        int size = containerSize(menu);
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            if (this.neededItems.contains(item)) {
                continue;
            }
            if (countItemInInventory(player, item) < threshold) {
                this.neededItems.add(item);
            }
        }
    }

    private static int materialThreshold() {
        if (Configs.Print.PRINT_RESERVE_ITEMS.getBooleanValue()) {
            return Math.max(1, Configs.Print.PRINT_RESERVE_ITEM_COUNT.getIntegerValue());
        }
        return 1;
    }

    private static int countItemInInventory(LocalPlayer player, Item item) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && (stack.getItem() == item || stack.is(item))) {
                total += stack.getCount();
            }
        }
        return total;
    }


    private boolean canMergeNeededIntoInventory(LocalPlayer player, ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(stack, incoming)
                    && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean ensureInventorySpace(LocalPlayer player, AbstractContainerMenu menu, int minEmpty, boolean allowDepositToContainer, boolean allowNeeded) {
        if (countEmptySlots(player) >= minEmpty) {
            return true;
        }
        if (!allowDepositToContainer) {
            return false;
        }
        int invSlot = findDisposablePickBlockableHotbarSlot(player, allowNeeded);
        if (invSlot < 0) {
            return false;
        }
        int menuSlot = inventorySlotToMenuSlot(menu, invSlot);
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return false;
        }
        if (tryDepositHotbarStackToContainer(player, menu, menuSlot)) {
            this.clickCooldown = CLICK_DELAY;
            return true;
        }
        return false;
    }

    private boolean ensureInventorySpaceClosed(LocalPlayer player, int minEmpty) {
        return countEmptySlots(player) >= minEmpty;
    }

    private boolean tryDepositHotbarStackToContainer(LocalPlayer player, AbstractContainerMenu menu, int invMenuSlot) {
        if (this.client.gameMode == null) {
            return false;
        }
        int size = containerSize(menu);
        boolean hasRoom = false;
        ItemStack source = menu.slots.get(invMenuSlot).getItem();
        if (source.isEmpty() || isShulker(source) || isNeeded(source)) {
            return false;
        }
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()
                    || ItemStack.isSameItemSameComponents(stack, source)
                    && stack.getCount() < stack.getMaxStackSize()) {
                hasRoom = true;
                break;
            }
        }
        if (!hasRoom) {
            return false;
        }
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, invMenuSlot, 0, ContainerInput.QUICK_MOVE, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, invMenuSlot, 0, ClickType.QUICK_MOVE, player);
        //#endif
        return menu.slots.get(invMenuSlot).getItem().isEmpty() || countEmptySlots(player) > 0;
    }

    private void throwMenuSlot(LocalPlayer player, AbstractContainerMenu menu, int menuSlot) {
        if (this.client.gameMode == null || menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return;
        }
        ItemStack stack = menu.slots.get(menuSlot).getItem();
        if (stack.isEmpty() || isShulker(stack)) {
            return;
        }
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, menuSlot, 1, ContainerInput.THROW, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, menuSlot, 1, ClickType.THROW, player);
        //#endif
    }

    private int findDisposablePickBlockableHotbarSlot(LocalPlayer player, boolean allowNeeded) {
        Inventory inv = player.getInventory();
        List<Integer> pickSlots = getPickBlockableHotbarSlots();
        int fallback = -1;
        for (int slot : pickSlots) {
            if (slot < 0 || slot > 8) {
                continue;
            }
            try {
                if (!InventoryUtilsAccessor.canPickToSlot(inv, slot)) {
                    continue;
                }
            } catch (Throwable ignored) {
            }
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || isShulker(stack)) {
                continue;
            }
            if (!isNeeded(stack)) {
                return slot;
            }
            if (allowNeeded && fallback < 0) {
                fallback = slot;
            }
        }
        return fallback;
    }

    private static List<Integer> getPickBlockableHotbarSlots() {
        try {
            List<Integer> configured = InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS();
            if (configured != null && !configured.isEmpty()) {
                return configured;
            }
        } catch (Throwable ignored) {
        }
        return List.of(0, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    private static int countEmptySlots(LocalPlayer player) {
        int empty = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    private boolean canTakeStack(LocalPlayer player, ItemStack source) {
        if (countEmptySlots(player) > 1) {
            return true;
        }
        return canMergeIntoPartialStacks(player, source);
    }

    private static boolean canMergeIntoPartialStacks(LocalPlayer player, ItemStack incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, incoming)
                    && stack.getCount() < stack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private static int findPartialMergeMenuSlot(AbstractContainerMenu menu, ItemStack carried) {
        if (carried == null || carried.isEmpty()) {
            return -1;
        }
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 36) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()
                    && ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < stack.getMaxStackSize()) {
                return i;
            }
        }
        return -1;
    }

    private boolean returnCarriedToContainer(LocalPlayer player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return true;
        }
        if (this.client.gameMode == null) {
            return false;
        }
        int size = containerSize(menu);
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()
                    || ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < stack.getMaxStackSize()) {
                //#if MC > 260100
                this.client.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.PICKUP, player);
                //#else
                //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, i, 0, ClickType.PICKUP, player);
                //#endif
                this.clickCooldown = CLICK_DELAY;
                return true;
            }
        }
        return false;
    }

    private void enqueue(RefillKind kind, Set<Item> items, boolean highPriority) {
        if (kind == null || items == null || items.isEmpty()) {
            return;
        }
        if (isActiveOrQueued(kind, items)) {
            return;
        }
        RefillTask task = new RefillTask(kind, new HashSet<>(items));
        if (highPriority) {
            this.queue.addFirst(task);
        } else {
            this.queue.addLast(task);
        }
    }

    private boolean isActiveOrQueued(RefillKind kind, Set<Item> items) {
        if (this.phase != Phase.IDLE && this.activeKind == kind) {
            if (kind != RefillKind.MATERIALS || this.neededItems.equals(items)) {
                return true;
            }
        }
        for (RefillTask task : this.queue) {
            if (task.kind == kind) {
                if (kind != RefillKind.MATERIALS || task.items.equals(items)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void scanAutoRefills(LocalPlayer player) {
        Inventory inv = player.getInventory();
        if (enderRefillEnabled()) {
            ItemStack ender = inv.getItem(enderHotbarSlot());
            if (ender.isEmpty()
                    || (isEnderChest(ender) && ender.getCount() <= 4)) {
                enqueue(RefillKind.ENDER_CHEST_STACK, Set.of(Items.ENDER_CHEST), true);
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_ENCHANTED_GOLDEN_APPLE.getBooleanValue()) {
            ItemStack gapple = inv.getItem(gappleHotbarSlot());
            int count = isGapple(gapple) ? gapple.getCount() : 0;
            if (count < 10) {
                enqueue(RefillKind.ENCHANTED_GOLDEN_APPLE, Set.of(Items.ENCHANTED_GOLDEN_APPLE), false);
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_PICKAXE.getBooleanValue()) {
            ItemStack pick = inv.getItem(toolHotbarSlot(RefillKind.NETHERITE_PICKAXE));
            if (!isUsableTool(pick, RefillKind.NETHERITE_PICKAXE)) {
                enqueue(RefillKind.NETHERITE_PICKAXE, Set.of(Items.NETHERITE_PICKAXE), true);
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_SHOVEL.getBooleanValue()) {
            ItemStack shovel = inv.getItem(toolHotbarSlot(RefillKind.NETHERITE_SHOVEL));
            if (!isUsableTool(shovel, RefillKind.NETHERITE_SHOVEL)) {
                enqueue(RefillKind.NETHERITE_SHOVEL, Set.of(Items.NETHERITE_SHOVEL), true);
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_AXE.getBooleanValue()) {
            ItemStack axe = inv.getItem(toolHotbarSlot(RefillKind.NETHERITE_AXE));
            if (!isUsableTool(axe, RefillKind.NETHERITE_AXE)) {
                enqueue(RefillKind.NETHERITE_AXE, Set.of(Items.NETHERITE_AXE), true);
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_HOE.getBooleanValue()) {
            ItemStack hoe = inv.getItem(toolHotbarSlot(RefillKind.NETHERITE_HOE));
            if (!isUsableTool(hoe, RefillKind.NETHERITE_HOE)) {
                enqueue(RefillKind.NETHERITE_HOE, Set.of(Items.NETHERITE_HOE), true);
            }
        }
    }

    private void tryStartQueued(LocalPlayer player) {
        if (this.refillLockedOut || this.refillFailed) {
            this.queue.clear();
            this.pendingNeeded.clear();
            return;
        }
        if (this.phase != Phase.IDLE) {
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        int attempts = this.queue.size();
        while (!this.queue.isEmpty() && attempts-- > 0) {
            RefillTask task = this.queue.pollFirst();
            if (task == null) {
                return;
            }
            if (startTask(player, task)) {
                return;
            }
            if (isToolRefillKind(task.kind)) {
                this.queue.addLast(task);
            }
        }
    }

    private boolean startTask(LocalPlayer player, RefillTask task) {
        if (player.containerMenu != player.inventoryMenu) {
            this.queue.addFirst(task);
            return false;
        }
        this.activeKind = task.kind;
        this.targetInvSlot = -1;
        this.takeRemaining = 0;
        this.pickaxeSwapDone = false;
        this.pickaxeSwapStage = 0;
        this.pickaxeTempInvSlot = -1;
        this.pickaxeContainerSlot = -1;
        this.enderFetchActive = false;
        Set<Item> needed = new HashSet<>(task.items);
        if (task.kind == RefillKind.ENDER_CHEST_STACK) {
            ItemStack stack = player.getInventory().getItem(enderHotbarSlot());
            if (!stack.isEmpty() && !isEnderChest(stack)) {
                failAndStop("hotbar slot " + enderHotbarSlot() + " is occupied by non-ender-chest item");
                return false;
            }
            int need = stack.isEmpty()
                    ? new ItemStack(Items.ENDER_CHEST).getMaxStackSize()
                    : Math.max(0, stack.getMaxStackSize() - stack.getCount());
            if (need <= 0) {
                return false;
            }
            this.targetInvSlot = enderHotbarSlot();
            this.takeRemaining = need;
        } else if (task.kind == RefillKind.ENCHANTED_GOLDEN_APPLE) {
            ItemStack stack = player.getInventory().getItem(gappleHotbarSlot());
            if (!stack.isEmpty() && !isGapple(stack)) {
                failAndStop("hotbar slot " + gappleHotbarSlot() + " is occupied by non-gapple item");
                return false;
            }
            int need = stack.isEmpty()
                    ? new ItemStack(Items.ENCHANTED_GOLDEN_APPLE).getMaxStackSize()
                    : Math.max(0, stack.getMaxStackSize() - stack.getCount());
            if (need <= 0) {
                return false;
            }
            this.targetInvSlot = gappleHotbarSlot();
            this.takeRemaining = need;
        } else if (isToolRefillKind(task.kind)) {
            this.targetInvSlot = toolHotbarSlot(task.kind);
            ItemStack tool = player.getInventory().getItem(toolHotbarSlot(task.kind));
            if (isUsableTool(tool, task.kind)) {
                return false;
            }
            if (findShulkerWithUsableTool(player, task.kind) < 0
                    && (!enderRefillEnabled() || findEnderChestSlot(player) < 0)) {
                return false;
            }
        }

        int slot = resolveInventoryShulkerSlot(player, needed);
        if (slot >= 0) {
            begin(player, slot, needed);
            return true;
        }
        if (tryStartEnderFetch(player, needed)) {
            return true;
        }
        if (task.kind == RefillKind.MATERIALS) {
            failAndStop("no shulker/ender source containing materials: " + needed);
            return false;
        }
        return false;
    }

    private int resolveInventoryShulkerSlot(LocalPlayer player, Set<Item> needed) {
        if (isToolRefillKind(this.activeKind)) {
            return findShulkerWithUsableTool(player, this.activeKind);
        }
        return findShulkerSlot(player, needed);
    }

    private boolean tryStartEnderFetch(LocalPlayer player, Set<Item> needed) {
        if (!enderRefillEnabled() || this.refillLockedOut || this.refillFailed) {
            return false;
        }
        if (this.enderFetchCooldownUntilTick == Long.MAX_VALUE
                || RuntimeAccess.get().currentTick() < this.enderFetchCooldownUntilTick) {
            return false;
        }
        if (findEnderChestSlot(player) < 0) {
            return false;
        }
        if (countEmptySlots(player) < 1 && !ensureInventorySpaceClosed(player, 1)) {
            return false;
        }
        this.enderFetchActive = true;
        this.enderFetchKind = this.activeKind;
        this.enderFetchTargetSlot = this.targetInvSlot;
        this.enderFetchTakeRemaining = this.takeRemaining;
        beginEnder(player, needed);
        return this.phase == Phase.EQUIP_ENDER;
    }

    private void tryStartPending(LocalPlayer player) {
        if (this.pendingNeeded.isEmpty()) {
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        Set<Item> needed = new HashSet<>(this.pendingNeeded);
        this.pendingNeeded.clear();
        enqueue(RefillKind.MATERIALS, needed, false);
        tryStartQueued(player);
    }

    private static int findHotbarEnderChestSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (isEnderChest(inv.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static int countHotbarItem(LocalPlayer player, Item item) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty() && (stack.getItem() == item || stack.is(item))) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int findIncompleteHotbarStack(LocalPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty()
                    && (stack.getItem() == item || stack.is(item))
                    && stack.getCount() < stack.getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    private static int findItemSlotPreferHotbar(LocalPlayer player, Item item) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty() && (stack.getItem() == item || stack.is(item))) {
                return slot;
            }
        }
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!stack.isEmpty() && (stack.getItem() == item || stack.is(item))) {
                return slot;
            }
        }
        return -1;
    }

    private int findLowDurabilityHotbarPickaxe(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int bestSlot = -1;
        float bestRemaining = 1.0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isToolAtOrBelowMinDurability(stack, this.activeKind)) {
                continue;
            }
            float remaining = remainingDurabilityPercent(stack);
            if (remaining < bestRemaining) {
                bestRemaining = remaining;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private boolean hasNetheritePickaxeInHotbar(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (isMatchingTool(inv.getItem(slot), this.activeKind)) {
                return true;
            }
        }
        return false;
    }

    private static int findEmptyInventoryMenuSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!(slot.container instanceof Inventory)) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            if (invIndex < 0 || invIndex >= 9) {
                continue;
            }
            if (slot.getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int findShulkerWithUsableTool(LocalPlayer player, RefillKind kind) {
        Inventory inv = player.getInventory();
        int bestSlot = -1;
        int bestUsableCount = Integer.MAX_VALUE;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack) || !shulkerHasUsableTool(stack, kind)) {
                continue;
            }
            int usable = countUsableShulkerTools(stack, kind);
            if (usable < bestUsableCount) {
                bestUsableCount = usable;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static boolean shulkerHasUsableTool(ItemStack shulker, RefillKind kind) {
        return countUsableShulkerTools(shulker, kind) > 0;
    }

    private static int countUsableShulkerTools(ItemStack shulker, RefillKind kind) {
        if (!isShulker(shulker) || !isToolRefillKind(kind)) {
            return 0;
        }
        int count = 0;
        for (ItemStack inner : listShulkerContents(shulker)) {
            if (isUsableTool(inner, kind)) {
                count++;
            }
        }
        return count;
    }

    private static List<ItemStack> listShulkerContents(ItemStack shulker) {
        List<ItemStack> items = new ArrayList<>();
        if (shulker == null || shulker.isEmpty()) {
            return items;
        }
        //#if MC >= 12006
        try {
            ItemContainerContents contents = shulker.get(DataComponents.CONTAINER);
            if (contents != null) {
                collectContainerContents(contents, items);
            }
        } catch (Throwable ignored) {
        }
        if (!items.isEmpty()) {
            return items;
        }
        //#endif
        try {
            for (ItemStack inner : fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(shulker, -1)) {
                if (inner != null && !inner.isEmpty()) {
                    items.add(inner.copy());
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    //#if MC >= 12006
    private static void collectContainerContents(ItemContainerContents contents, List<ItemStack> out) {
        if (invokeStackStream(contents, "stream", out)) {
            return;
        }
        if (invokeStackStream(contents, "copyOneStackStream", out)) {
            return;
        }
        if (invokeStackStream(contents, "nonEmptyStream", out)) {
            return;
        }
        try {
            Object itemsObj = contents.getClass().getMethod("items").invoke(contents);
            if (itemsObj instanceof Iterable<?> iterable) {
                for (Object o : iterable) {
                    if (o instanceof ItemStack stack && !stack.isEmpty()) {
                        out.add(stack.copy());
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
    //#endif

    private static boolean invokeStackStream(Object target, String methodName, List<ItemStack> out) {
        try {
            Object stream = target.getClass().getMethod(methodName).invoke(target);
            if (stream == null) {
                return false;
            }
            Object listObj = stream.getClass().getMethod("toList").invoke(stream);
            if (!(listObj instanceof List<?> list)) {
                return false;
            }
            int before = out.size();
            for (Object o : list) {
                if (o instanceof ItemStack stack && !stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
            return out.size() > before;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isUsableTool(ItemStack stack, RefillKind kind) {
        if (!isMatchingTool(stack, kind)) {
            return false;
        }
        return !isToolAtOrBelowMinDurability(stack, kind);
    }

    private static boolean isMatchingTool(ItemStack stack, RefillKind kind) {
        if (stack.isEmpty() || !isToolRefillKind(kind)) {
            return false;
        }
        Item expected = toolItem(kind);
        if (stack.getItem() == expected || stack.is(expected)) {
            return true;
        }
        try {
            String frag = toolIdFragment(kind);
            return !frag.isEmpty() && stack.getItem().toString().contains(frag);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isBelowDurabilityPercent(ItemStack stack, float threshold) {
        return remainingDurabilityPercent(stack) <= threshold;
    }

    private static boolean isToolAtOrBelowMinDurability(ItemStack stack, RefillKind kind) {
        if (!isMatchingTool(stack, kind)) {
            return false;
        }
        float threshold = toolMinDurabilityFraction(kind);
        return remainingDurabilityPercent(stack) <= threshold;
    }

    private static float remainingDurabilityPercent(ItemStack stack) {
        if (stack.isEmpty()) {
            return 1.0f;
        }
        int max = stackMaxDamage(stack);
        if (max <= 0) {
            max = stack.getMaxDamage();
        }
        if (max <= 0) {
            return 1.0f;
        }
        int damage = readStackDamage(stack);
        if (damage < 0) {
            damage = stack.getDamageValue();
        }
        if (damage < 0) {
            damage = 0;
        }
        if (damage > max) {
            damage = max;
        }
        return (float) (max - damage) / (float) max;
    }

    private static int stackMaxDamage(ItemStack stack) {
        try {
            int max = stack.getMaxDamage();
            if (max > 0) {
                return max;
            }
        } catch (Throwable ignored) {
        }
        //#if MC >= 12006
        try {
            Integer component = stack.get(DataComponents.MAX_DAMAGE);
            if (component != null && component > 0) {
                return component;
            }
        } catch (Throwable ignored) {
        }
        //#endif
        return 0;
    }

    private static int readStackDamage(ItemStack stack) {
        int damage = 0;
        try {
            damage = Math.max(damage, stack.getDamageValue());
        } catch (Throwable ignored) {
        }
        //#if MC >= 12006
        try {
            Integer component = stack.get(DataComponents.DAMAGE);
            if (component != null) {
                damage = Math.max(damage, component);
            }
        } catch (Throwable ignored) {
        }
        //#endif
        return damage;
    }


    private static int findShulkerSlot(LocalPlayer player, Set<Item> needed) {
        Inventory inv = player.getInventory();
        int bestSlot = -1;
        int bestItemCount = Integer.MAX_VALUE;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack) || !containsNeeded(stack, needed)) {
                continue;
            }
            int itemCount = countStoredItems(stack);
            if (itemCount < bestItemCount) {
                bestItemCount = itemCount;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static int countStoredItems(ItemStack shulker) {
        int total = 0;
        for (ItemStack inner : listShulkerContents(shulker)) {
            if (!inner.isEmpty()) {
                total += inner.getCount();
            }
        }
        return total;
    }

    private static boolean containsNeeded(ItemStack shulker, Set<Item> needed) {
        if (needed == null || needed.isEmpty() || !isShulker(shulker)) {
            return false;
        }
        for (ItemStack inner : listShulkerContents(shulker)) {
            if (inner.isEmpty()) {
                continue;
            }
            for (Item item : needed) {
                if (item != null && (inner.getItem() == item || inner.is(item))) {
                    return true;
                }
            }
        }
        return false;
    }

    private BlockPos findNearbyPlacedShulker(LocalPlayer player) {
        if (this.client.level == null) {
            return null;
        }
        BlockPos origin = blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ());
        if (this.placedPos != null
                && this.client.level.getBlockState(this.placedPos).getBlock() instanceof ShulkerBoxBlock
                && this.client.level.getBlockEntity(this.placedPos) instanceof ShulkerBoxBlockEntity) {
            return this.placedPos;
        }
        for (int dy = 0; dy <= 3; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (this.client.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock
                            && this.client.level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private InteractionResult vanillaUseItemOn(InteractionHand hand, BlockHitResult hit) {
        if (this.client.gameMode == null || this.client.player == null || this.client.level == null) {
            return InteractionResult.FAIL;
        }
        //#if MC > 11802
        return this.client.gameMode.useItemOn(this.client.player, hand, hit);
        //#else
        //$$ return this.client.gameMode.useItemOn(this.client.player, this.client.level, hand, hit);
        //#endif
    }

    private boolean isRealPlacedShulker() {
        if (this.placedPos == null || this.client.level == null) {
            return false;
        }
        if (!(this.client.level.getBlockState(this.placedPos).getBlock() instanceof ShulkerBoxBlock)) {
            return false;
        }
        BlockEntity be = this.client.level.getBlockEntity(this.placedPos);
        return be instanceof ShulkerBoxBlockEntity;
    }

    private void selectEmptyHand(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) {
                InventoryUtils.setSelectedSlot(inv, i);
                InventoryUtils.syncSelectedHotbarSlot();
                return;
            }
        }
        int selected = InventoryUtils.getSelectedSlot(inv);
        if (!inv.getItem(selected).isEmpty() && isShulker(inv.getItem(selected))) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getItem(i);
                if (!isShulker(stack) && !(stack.getItem() instanceof BlockItem)) {
                    InventoryUtils.setSelectedSlot(inv, i);
                    InventoryUtils.syncSelectedHotbarSlot();
                    return;
                }
            }
        }
    }

    private static BlockPos blockPosAt(double x, double y, double z) {
        //#if MC > 11802
        return BlockPos.containing(x, y, z);
        //#else
        //$$ return new BlockPos(x, y, z);
        //#endif
    }

    private static boolean isGapple(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE || stack.is(Items.ENCHANTED_GOLDEN_APPLE));
    }

    private static boolean isShulker(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private void shutdownIfInactive() {
        if (this.phase != Phase.IDLE) {
            this.pendingNeeded.clear();
            this.queue.clear();
            this.rejectedEnderPickaxeSlots.clear();
            abortKeepWorld();
            return;
        }
        if (!this.pendingNeeded.isEmpty() || !this.queue.isEmpty()) {
            this.pendingNeeded.clear();
            this.queue.clear();
        }
        this.rejectedEnderPickaxeSlots.clear();
    }

    private static boolean enabled() {
        return Configs.Special.MANUAL_VANILLA_REFILL.getBooleanValue()
                && Configs.Core.WORK_SWITCH.getBooleanValue();
    }

    private static boolean enderRefillEnabled() {
        return enabled() && Configs.Special.MANUAL_VANILLA_REFILL_FROM_ENDER.getBooleanValue();
    }

    private static int configuredEnderCount() {
        if (Configs.Special.MANUAL_VANILLA_REFILL_ENDER_COUNT.getOptionListValue() instanceof EnderChestCountType type) {
            return Math.max(1, Math.min(2, type.count()));
        }
        return 1;
    }

    private void beginEnder(LocalPlayer player, Set<Item> needed) {
        int enderSlot = findEnderChestSlot(player);
        if (enderSlot < 0) {
            return;
        }
        this.neededItems.clear();
        this.neededItems.addAll(needed);
        this.enderMode = true;
        this.enderInvSlot = enderSlot;
        this.shulkerInvSlot = -1;
        this.placedPos = null;
        this.placedEnderPositions.clear();
        this.enderPlaceIndex = 0;
        this.enderBreakIndex = 0;
        this.placementCommitted = false;
        this.savedLook = new PlayerLook(player.getYRot(), player.getXRot());
        this.wasSneaking = player.isShiftKeyDown();
        if (this.wasSneaking) {
            RuntimeAccess.get().actionBroker().setShift(player, false);
        }
        this.deadline = RuntimeAccess.get().currentTick() + GLOBAL_TIMEOUT_TICKS;
        this.openAttempts = 0;
        this.settleTicks = 0;
        this.clickCooldown = 0;
        this.expectedContainerId = -1;
        this.contentReady = false;
        this.issuedTakeClick = false;
        this.tookItems = false;
        this.takePhaseDone = false;
        if (!this.refillLockedOut) {
            this.refillFailed = false;
        }
        this.inventorySnapshot = Map.of();
        RuntimeAccess.get().actionBroker().cancelQueue();
        RuntimeAccess.get().inventorySwitchGuard().reset();
        stopExternalWork();
        RuntimeAccess.get().actionBroker().tryAcquire(
                LEASE_OWNER,
                EnumSet.of(ResourceLease.CONTAINER, ResourceLease.MAIN_HAND, ResourceLease.INTERACTION),
                0L
        );
        this.phase = Phase.EQUIP_ENDER;
    }

    private void tickEquipEnder(LocalPlayer player) {
        ItemStack stack = player.getInventory().getItem(this.enderInvSlot);
        if (!isEnderChest(stack)) {
            int found = findEnderChestSlot(player);
            if (found < 0) {
                failAndStop("no ender chest in inventory to place for refill");
                return;
            }
            this.enderInvSlot = found;
            stack = player.getInventory().getItem(found);
        }
        if (Inventory.isHotbarSlot(this.enderInvSlot)) {
            InventoryUtils.setSelectedSlot(player.getInventory(), this.enderInvSlot);
            InventoryUtils.syncSelectedHotbarSlot();
        } else {
            int hotbar = findEquipHotbarSlot(player);
            if (hotbar < 0 || this.client.gameMode == null) {
                failAndStop("could not move ender chest to hotbar for placement");
                return;
            }
            AbstractContainerMenu menu = player.inventoryMenu;
            int sourceMenuSlot = findInventoryMenuSlot(menu, this.enderInvSlot);
            if (sourceMenuSlot < 0) {
                failAndStop("could not move ender chest to hotbar for placement");
                return;
            }
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, hotbar, ContainerInput.SWAP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, sourceMenuSlot, hotbar, ClickType.SWAP, player);
            //#endif
            this.enderInvSlot = hotbar;
            InventoryUtils.setSelectedSlot(player.getInventory(), hotbar);
            InventoryUtils.syncSelectedHotbarSlot();
        }
        this.phase = Phase.PLACE_ENDER;
        this.settleTicks = 0;
        this.enderPlaceIndex = 0;
        this.placedEnderPositions.clear();
    }

    private void tickPlaceEnder(LocalPlayer player) {
        int targetCount = configuredEnderCount();
        if (this.enderPlaceIndex >= targetCount) {
            selectEmptyHand(player);
            stopExternalWork();
            this.phase = Phase.OPEN_ENDER;
            this.settleTicks = 0;
            this.openAttempts = 0;
            return;
        }
        if (!isEnderChest(player.getMainHandItem())) {
            int found = findEnderChestSlot(player);
            if (found < 0) {
                if (!this.placedEnderPositions.isEmpty()) {
                    selectEmptyHand(player);
                    this.phase = Phase.OPEN_ENDER;
                    this.settleTicks = 0;
                    this.openAttempts = 0;
                    return;
                }
                failAndStop("no ender chest left to place for double-ender refill");
                return;
            }
            this.enderInvSlot = found;
            if (Inventory.isHotbarSlot(found)) {
                InventoryUtils.setSelectedSlot(player.getInventory(), found);
                InventoryUtils.syncSelectedHotbarSlot();
            } else {
                this.phase = Phase.EQUIP_ENDER;
                this.settleTicks = 0;
                return;
            }
        }
        if (isLavaNearby(player, LAVA_SAFE_DISTANCE)) {
            enterLavaWait();
            return;
        }
        BlockPos target = resolveEnderPlacePos(player, this.enderPlaceIndex);
        if (target == null) {
            target = blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ());
        }
        BlockState targetState = this.client.level.getBlockState(target);
        if (isLavaBlock(targetState)) {
            enterLavaWait();
            return;
        }
        exitLavaWait();
        if (!isReplaceableForPlace(targetState)) {
            if (isRealPlacedEnder(target)) {
                if (!this.placedEnderPositions.contains(target)) {
                    this.placedEnderPositions.add(target);
                }
                this.enderPlaceIndex++;
                this.settleTicks = 0;
                return;
            }
            this.placedPos = target;
            this.clearResumePhase = Phase.EQUIP_ENDER;
            this.phase = Phase.CLEAR_PLACE;
            this.settleTicks = 0;
            return;
        }
        lookAt(player, target);
        BlockHitResult hit = placeHitResult(target);
        InteractionResult result = vanillaUseItemOn(InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction() || isRealPlacedEnder(target)) {
            this.placedEnderPositions.add(target);
            this.enderPlaceIndex++;
            this.phase = Phase.SETTLE_ENDER;
            this.settleTicks = 0;
        } else if (++this.settleTicks > 20) {
            failAndStop("ender chest placement not accepted by server");
        }
    }

    private void tickSettleEnder(LocalPlayer player) {
        if (this.placedEnderPositions.isEmpty()) {
            failAndStop("ender settle with no placed positions");
            return;
        }
        BlockPos last = this.placedEnderPositions.get(this.placedEnderPositions.size() - 1);
        if (!isRealPlacedEnder(last)) {
            if (++this.settleTicks > PLACE_CONFIRM_TICKS) {
                failAndStop("placed ender chest disappeared before open");
            }
            return;
        }
        if (++this.settleTicks < PLACE_SETTLE_TICKS) {
            return;
        }
        int targetCount = configuredEnderCount();
        if (this.enderPlaceIndex < targetCount) {
            this.phase = Phase.PLACE_ENDER;
            this.settleTicks = 0;
            return;
        }
        BlockPos openTarget = primaryEnderPos(player);
        if (openTarget != null && beginClearAbove(openTarget, Phase.OPEN_ENDER)) {
            return;
        }
        selectEmptyHand(player);
        stopExternalWork();
        this.phase = Phase.OPEN_ENDER;
        this.settleTicks = 0;
        this.openAttempts = 0;
    }

    private void tickOpenEnder(LocalPlayer player) {
        BlockPos openPos = primaryEnderPos(player);
        if (openPos == null) {
            failAndStop("no placed ender chest available to open");
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            this.expectedContainerId = player.containerMenu.containerId;
            this.phase = Phase.WAIT_CONTENT_ENDER;
            this.settleTicks = 0;
            return;
        }
        if (!isRealPlacedEnder(openPos)) {
            failAndStop("placed ender chest missing or invalid at open target");
            return;
        }
        if (beginClearAbove(openPos, Phase.OPEN_ENDER)) {
            return;
        }
        selectEmptyHand(player);
        stopExternalWork();
        lookAt(player, openPos);
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(openPos),
                Direction.UP,
                openPos,
                false
        );
        InteractionResult result = vanillaUseItemOn(InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction() && player.containerMenu == player.inventoryMenu) {
            result = vanillaUseItemOn(InteractionHand.OFF_HAND, hit);
        }
        this.openAttempts++;
        this.phase = Phase.WAIT_CONTENT_ENDER;
        this.settleTicks = 0;
        this.contentReady = false;
    }

    private void tickWaitContentEnder(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            if (this.expectedContainerId < 0) {
                this.expectedContainerId = player.containerMenu.containerId;
            }
            pruneRejectedEnderSlots(player.containerMenu);
            if (this.enderStashMode
                    || this.contentReady
                    || findShulkerInContainer(player.containerMenu) >= 0) {
                this.contentReady = true;
                this.phase = Phase.TAKE_SHULKER;
                this.settleTicks = 0;
                this.clickCooldown = CLICK_DELAY;
                return;
            }
            if (++this.settleTicks > CONTENT_WAIT_TICKS) {
                AbstractContainerMenu menu = player.containerMenu;
                int size = containerSize(menu);
                failAndStop("ender chest open but no matching shulker (containerSize=" + size
                        + " menuSlots=" + menu.slots.size()
                        + " shulkers=" + countContainerShulkers(menu)
                        + " needed=" + this.neededItems
                        + " kind=" + this.activeKind + ")");
            }
            return;
        }
        if (++this.settleTicks > 20) {
            retryOpenEnderOrAbort(player);
        }
    }

    private void retryOpenEnderOrAbort(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        this.contentReady = false;
        this.expectedContainerId = -1;
        failAndStop("could not open ender chest after "
                + this.openAttempts + " attempts");
    }

    private void tickTakeShulker(LocalPlayer player) {
        if (player.containerMenu == player.inventoryMenu) {
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE_ENDER;
            this.settleTicks = 0;
            return;
        }
        if (this.clickCooldown > 0) {
            return;
        }
        if (this.enderStashMode) {
            tickStashShulkerToEnder(player);
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) {
            if (!returnCarriedToContainer(player, menu)) {
                failAndStop("could not return carried item to ender chest after shulker take");
            }
            return;
        }
        if (countEmptySlots(player) < 1) {
            failAndStop("no free inventory slot to take shulker from ender chest");
            return;
        }
        int slot = findShulkerInContainer(menu);
        if (slot < 0) {
            if (hasContainerShulker(menu) && this.settleTicks < CONTENT_WAIT_TICKS) {
                this.settleTicks++;
                return;
            }
            int size = containerSize(menu);
            int shulkerCount = countContainerShulkers(menu);
            failAndStop("no matching shulker in ender chest (containerSize=" + size
                    + " shulkers=" + shulkerCount
                    + " needed=" + this.neededItems
                    + " kind=" + this.activeKind
                    + " menuSlots=" + menu.slots.size() + ")");
            return;
        }
        this.settleTicks = 0;
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, player);
        //#endif
        this.issuedTakeClick = true;
        this.clickCooldown = CLICK_DELAY;
        this.tookItems = true;
        this.takePhaseDone = true;
        this.phase = Phase.CLOSE_ENDER;
        this.settleTicks = 0;
    }

    private void tickStashShulkerToEnder(LocalPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        pruneRejectedEnderSlots(menu);
        if (!menu.getCarried().isEmpty()) {
            int empty = findEmptyContainerSlot(menu);
            if (empty < 0) {
                this.takePhaseDone = true;
                this.phase = Phase.CLOSE_ENDER;
                this.settleTicks = 0;
                return;
            }
            //#if MC > 260100
            this.client.gameMode.handleContainerInput(menu.containerId, empty, 0, ContainerInput.PICKUP, player);
            //#else
            //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, empty, 0, ClickType.PICKUP, player);
            //#endif
            this.rejectedEnderPickaxeSlots.add(empty);
            this.issuedTakeClick = true;
            this.tookItems = true;
            this.takePhaseDone = true;
            this.clickCooldown = CLICK_DELAY;
            this.phase = Phase.CLOSE_ENDER;
            this.settleTicks = 0;
            return;
        }
        int invMenu = inventorySlotToMenuSlot(menu, this.stashShulkerInvSlot);
        if (invMenu < 0 || invMenu >= menu.slots.size() || !isShulker(menu.slots.get(invMenu).getItem())) {
            invMenu = findWornPickaxeShulkerMenuSlot(menu);
        }
        if (invMenu < 0) {
            invMenu = findAnyPickaxeShulkerMenuSlot(menu);
        }
        if (invMenu < 0) {
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE_ENDER;
            this.settleTicks = 0;
            return;
        }
        this.stashShulkerInvSlot = menu.slots.get(invMenu).getContainerSlot();
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, invMenu, 0, ContainerInput.PICKUP, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, invMenu, 0, ClickType.PICKUP, player);
        //#endif
        this.issuedTakeClick = true;
        this.clickCooldown = CLICK_DELAY;
    }

    private int findEmptyContainerSlot(AbstractContainerMenu menu) {
        int size = Math.max(containerSize(menu), Math.max(0, menu.slots.size() - 36));
        size = Math.min(size, menu.slots.size());
        for (int i = 0; i < size; i++) {
            if (this.rejectedEnderPickaxeSlots.contains(i)) {
                continue;
            }
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            if (slot.getItem().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private int findWornPickaxeShulkerMenuSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!isShulker(stack)) {
                continue;
            }
            boolean hasTool = false;
            RefillKind searchKind = refillKindForShulkerSearch();
            for (ItemStack inner : listShulkerContents(stack)) {
                if (isMatchingTool(inner, searchKind)) {
                    hasTool = true;
                    break;
                }
            }
            if (hasTool && !shulkerHasUsableTool(stack, searchKind)) {
                return i;
            }
        }
        return -1;
    }

    private int findAnyPickaxeShulkerMenuSlot(AbstractContainerMenu menu) {
        int size = containerSize(menu);
        for (int i = size; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!isShulker(stack)) {
                continue;
            }
            RefillKind searchKind = refillKindForShulkerSearch();
            for (ItemStack inner : listShulkerContents(stack)) {
                if (isMatchingTool(inner, searchKind)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void tickCloseEnder(LocalPlayer player) {
        this.takePhaseDone = true;
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        this.phase = Phase.WAIT_CLOSE_ENDER;
        this.settleTicks = 0;
    }

    private void tickWaitCloseEnder(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            if (++this.settleTicks % 8 == 0) {
                player.closeContainer();
            }
            if (this.settleTicks > CLOSE_WAIT_TICKS) {
                this.phase = Phase.POST_CLOSE_ENDER;
                this.settleTicks = 0;
            }
            return;
        }
        this.phase = Phase.POST_CLOSE_ENDER;
        this.settleTicks = 0;
    }

    private void tickPostCloseEnder(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (++this.settleTicks < POST_CLOSE_TICKS) {
            return;
        }
        if (!this.placedEnderPositions.isEmpty()) {
            if (countEmptySlots(player) < 1) {
                ensureInventorySpaceClosed(player, 1);
            }
            RuntimeAccess.get().inventorySwitchGuard().reset();
            this.phase = Phase.BREAK_ENDER;
            this.settleTicks = 0;
            this.enderBreakIndex = 0;
        } else {
            transitionToShulkerRefill(player);
        }
    }

    private void tickBreakEnder(LocalPlayer player) {
        if (this.enderBreakIndex >= this.placedEnderPositions.size()) {
            this.phase = Phase.PICKUP_ENDER;
            this.settleTicks = 0;
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            this.phase = Phase.WAIT_CLOSE_ENDER;
            this.settleTicks = 0;
            return;
        }
        BlockPos pos = this.placedEnderPositions.get(this.enderBreakIndex);
        if (this.client.level.getBlockState(pos).isAir() || !isRealPlacedEnder(pos)) {
            this.enderBreakIndex++;
            this.settleTicks = 0;
            return;
        }
        this.placedPos = pos;
        lookAt(player, pos);
        if (!prepareBreakTool(player, pos)) {
            return;
        }
        startBreakShulker(player);
        this.phase = Phase.WAIT_BREAK_ENDER;
        this.settleTicks = 0;
    }

    private void tickWaitBreakEnder(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
            return;
        }
        if (this.enderBreakIndex >= this.placedEnderPositions.size()) {
            this.phase = Phase.PICKUP_ENDER;
            this.settleTicks = 0;
            return;
        }
        BlockPos pos = this.placedEnderPositions.get(this.enderBreakIndex);
        if (this.client.level.getBlockState(pos).isAir() || !(this.client.level.getBlockState(pos).getBlock() instanceof EnderChestBlock)) {
            this.enderBreakIndex++;
            this.settleTicks = 0;
            if (this.enderBreakIndex >= this.placedEnderPositions.size()) {
                this.phase = Phase.PICKUP_ENDER;
            } else {
                this.phase = Phase.BREAK_ENDER;
            }
            return;
        }
        this.placedPos = pos;
        lookAt(player, pos);
        if (!prepareBreakTool(player, pos)) {
            return;
        }
        continueBreakShulker(player);
        if (++this.settleTicks > 120) {
            this.enderBreakIndex++;
            this.settleTicks = 0;
            this.phase = Phase.BREAK_ENDER;
        }
    }

    private void tickPickupEnder(LocalPlayer player) {
        if (++this.settleTicks < POST_BREAK_MOVEMENT_LOCK_TICKS) {
            return;
        }
        if (this.refillFailed || this.refillLockedOut) {
            finalizeFailedRefill();
            return;
        }
        if (this.enderStashMode) {
            this.enderStashMode = false;
            this.stashShulkerInvSlot = -1;
            this.enderMode = false;
            this.placedEnderPositions.clear();
            this.placedPos = null;
            this.enderInvSlot = -1;
            this.phase = Phase.RESTORE;
            this.settleTicks = 0;
            return;
        }
        transitionToShulkerRefill(player);
    }

    private void transitionToShulkerRefill(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            player.closeContainer();
        }
        Set<Item> needed = new HashSet<>(this.neededItems);
        RefillKind kind = this.enderFetchActive ? this.enderFetchKind : this.activeKind;
        int savedTarget = this.enderFetchActive ? this.enderFetchTargetSlot : this.targetInvSlot;
        int savedRemaining = this.enderFetchActive ? this.enderFetchTakeRemaining : this.takeRemaining;
        this.enderMode = false;
        this.placedEnderPositions.clear();
        this.placedPos = null;
        this.enderInvSlot = -1;
        this.enderPlaceIndex = 0;
        this.enderBreakIndex = 0;
        this.placementCommitted = false;
        this.openAttempts = 0;
        this.settleTicks = 0;
        this.clickCooldown = 0;
        this.expectedContainerId = -1;
        this.contentReady = false;
        this.issuedTakeClick = false;
        this.tookItems = false;
        this.takePhaseDone = false;
        this.inventorySnapshot = Map.of();
        this.enderFetchActive = false;
        int slot;
        if (isToolRefillKind(kind)) {
            slot = findShulkerWithUsableTool(player, kind);
        } else {
            slot = findShulkerSlot(player, needed);
        }
        if (slot < 0) {
            if (this.refillFailed || this.refillLockedOut) {
                finalizeFailedRefill();
            } else {
                failAndStop("ender fetch finished without a usable inventory shulker (kind=" + kind + ")");
            }
            return;
        }
        this.activeKind = kind;
        this.targetInvSlot = savedTarget;
        this.takeRemaining = savedRemaining;
        this.pickaxeSwapDone = false;
        this.pickaxeSwapStage = 0;
        this.pickaxeTempInvSlot = -1;
        this.pickaxeContainerSlot = -1;
        begin(player, slot, needed);
        this.activeKind = kind;
        this.targetInvSlot = savedTarget;
        this.takeRemaining = savedRemaining;
        this.pickaxeSwapDone = false;
        this.pickaxeSwapStage = 0;
        this.pickaxeTempInvSlot = -1;
        this.pickaxeContainerSlot = -1;
    }

    @Nullable
    private BlockPos resolveEnderPlacePos(LocalPlayer player, int index) {
        BlockPos above = blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ());
        if (index == 0) {
            return above;
        }
        Direction[] dirs = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        for (Direction dir : dirs) {
            BlockPos side = above.relative(dir);
            if (isReplaceableForPlace(this.client.level.getBlockState(side))
                    && !isLavaNearbyPos(side, LAVA_SAFE_DISTANCE)) {
                return side;
            }
        }
        return findNearbyAir(player);
    }

    @Nullable
    private BlockPos primaryEnderPos(LocalPlayer player) {
        BlockPos above = blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ());
        if (isRealPlacedEnder(above)) {
            return above;
        }
        for (BlockPos pos : this.placedEnderPositions) {
            if (isRealPlacedEnder(pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isRealPlacedEnder(BlockPos pos) {
        if (pos == null || this.client.level == null) {
            return false;
        }
        return this.client.level.getBlockState(pos).getBlock() instanceof EnderChestBlock;
    }

    private static int findEnderChestSlot(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            if (isEnderChest(inv.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isEnderChest(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(Items.ENDER_CHEST)
                || (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof EnderChestBlock));
    }

    private RefillKind refillKindForShulkerSearch() {
        if (this.enderFetchActive) {
            return this.enderFetchKind;
        }
        return this.activeKind;
    }

    private void pruneRejectedEnderSlots(AbstractContainerMenu menu) {
        if (this.rejectedEnderPickaxeSlots.isEmpty() || menu == null) {
            return;
        }
        this.rejectedEnderPickaxeSlots.removeIf(slot -> {
            if (slot == null || slot < 0 || slot >= menu.slots.size()) {
                return true;
            }
            ItemStack stack = menu.slots.get(slot).getItem();
            if (stack.isEmpty() || !isShulker(stack)) {
                return true;
            }
            return !shulkerContainsTool(stack, refillKindForShulkerSearch());
        });
    }

    private int findShulkerInContainer(AbstractContainerMenu menu) {
        pruneRejectedEnderSlots(menu);
        int size = Math.max(containerSize(menu), Math.max(0, menu.slots.size() - 36));
        size = Math.min(size, menu.slots.size());
        int bestSlot = -1;
        int bestUsable = -1;
        int bestItemCount = Integer.MAX_VALUE;
        RefillKind kind = refillKindForShulkerSearch();
        for (int i = 0; i < size; i++) {
            if (isToolRefillKind(kind) && this.rejectedEnderPickaxeSlots.contains(i)) {
                continue;
            }
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!isShulker(stack)) {
                continue;
            }
            if (isToolRefillKind(kind)) {
                if (!shulkerContainsTool(stack, kind)) {
                    continue;
                }
                int usable = countUsableShulkerTools(stack, kind);
                int itemCount = countStoredItems(stack);
                if (usable > bestUsable
                        || (usable == bestUsable && itemCount < bestItemCount)
                        || bestSlot < 0) {
                    bestUsable = usable;
                    bestItemCount = itemCount;
                    bestSlot = i;
                }
                continue;
            }
            if (!this.neededItems.isEmpty() && !containsNeeded(stack, this.neededItems)) {
                continue;
            }
            int itemCount = countStoredItems(stack);
            if (itemCount < bestItemCount || bestSlot < 0) {
                bestItemCount = itemCount;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private static boolean hasContainerShulker(AbstractContainerMenu menu) {
        return countContainerShulkers(menu) > 0;
    }

    private static int countContainerShulkers(AbstractContainerMenu menu) {
        int size = Math.max(containerSize(menu), Math.max(0, menu.slots.size() - 36));
        size = Math.min(size, menu.slots.size());
        int count = 0;
        for (int i = 0; i < size; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            if (isShulker(slot.getItem())) {
                count++;
            }
        }
        return count;
    }

    private enum RefillKind {
        MATERIALS,
        ENDER_CHEST_STACK,
        ENCHANTED_GOLDEN_APPLE,
        NETHERITE_PICKAXE,
        NETHERITE_SHOVEL,
        NETHERITE_AXE,
        NETHERITE_HOE
    }

    private static final class RefillTask {
        final RefillKind kind;
        final Set<Item> items;

        RefillTask(RefillKind kind, Set<Item> items) {
            this.kind = kind;
            this.items = items;
        }
    }

    private enum Phase {
        IDLE,
        EQUIP,
        CLEAR_PLACE,
        WAIT_CLEAR_PLACE,
        PLACE,
        SETTLE_PLACE,
        OPEN,
        WAIT_CONTENT,
        TAKE,
        CLOSE,
        WAIT_CLOSE,
        POST_CLOSE,
        BREAK,
        WAIT_BREAK,
        PICKUP,
        RESTORE,
        EQUIP_ENDER,
        PLACE_ENDER,
        SETTLE_ENDER,
        OPEN_ENDER,
        WAIT_CONTENT_ENDER,
        TAKE_SHULKER,
        CLOSE_ENDER,
        WAIT_CLOSE_ENDER,
        POST_CLOSE_ENDER,
        BREAK_ENDER,
        WAIT_BREAK_ENDER,
        PICKUP_ENDER
    }
}
