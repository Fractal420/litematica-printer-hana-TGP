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
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
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
    private static final int PLACE_SETTLE_TICKS = 12;
    private static final int PLACE_CONFIRM_TICKS = 60;
    private static final int MAX_OPEN_ATTEMPTS = 8;
    private static final int CONTENT_WAIT_TICKS = 60;
    private static final int CLOSE_WAIT_TICKS = 40;
    private static final int POST_CLOSE_TICKS = 3;
    private static final int CLICK_DELAY = 2;
    private static final int POST_BREAK_MOVEMENT_LOCK_TICKS = 30;
    private static final float PICKAXE_MIN_DURABILITY = 0.02f;

    private final Minecraft client;
    private Phase phase = Phase.IDLE;
    private final Set<Item> neededItems = new HashSet<>();
    private final Set<Item> pendingNeeded = new HashSet<>();
    private int shulkerInvSlot = -1;
    @Nullable private BlockPos placedPos;
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
    private final Set<Integer> rejectedEnderPickaxeSlots = new HashSet<>();
    private boolean stashWornPickaxeShulker;
    private boolean enderStashMode;
    private int stashShulkerInvSlot = -1;
    private final Set<Integer> invShulkerSlotsSnapshot = new HashSet<>();

    public ManualVanillaRefillController(Minecraft client) {
        this.client = client;
    }

    public void requestItems(Collection<Item> items) {
        if (!enabled() || items == null || items.isEmpty()) {
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
        enqueue(RefillKind.MATERIALS, needed, false);
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
        return enabled() && this.phase != Phase.IDLE;
    }

    public boolean shouldBlockExternalBreaking() {
        return shouldPause()
                && this.phase != Phase.BREAK
                && this.phase != Phase.WAIT_BREAK
                && this.phase != Phase.BREAK_ENDER
                && this.phase != Phase.WAIT_BREAK_ENDER;
    }

    public boolean isAllowedBreakTarget(BlockPos pos) {
        if (!shouldPause() || pos == null) {
            return false;
        }
        if (this.phase == Phase.BREAK || this.phase == Phase.WAIT_BREAK) {
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
            this.phase = Phase.TAKE_SHULKER;
            this.settleTicks = 0;
            this.clickCooldown = CLICK_DELAY;
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
        if (this.phase == Phase.IDLE) {
            tryStartPending(player);
            if (this.phase == Phase.IDLE) {
                tryStartQueued(player);
            }
            return;
        }
        if (RuntimeAccess.get().currentTick() > this.deadline) {
            abortKeepWorld();
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
        ItemStack stack = player.getInventory().getItem(this.shulkerInvSlot);
        if (!isShulker(stack)) {
            abortKeepWorld();
            return;
        }
        if (this.activeKind == RefillKind.NETHERITE_PICKAXE) {
            if (countUsableShulkerPickaxes(stack) <= 0) {
                int alt = findShulkerWithUsablePickaxe(player);
                if (alt < 0) {
                    abortKeepWorld();
                    return;
                }
                this.shulkerInvSlot = alt;
                stack = player.getInventory().getItem(alt);
                if (!isShulker(stack) || countUsableShulkerPickaxes(stack) <= 0) {
                    abortKeepWorld();
                    return;
                }
            }
        } else if (!containsNeeded(stack, this.neededItems)) {
            abortKeepWorld();
            return;
        }
        if (Inventory.isHotbarSlot(this.shulkerInvSlot)) {
            InventoryUtils.setSelectedSlot(player.getInventory(), this.shulkerInvSlot);
            InventoryUtils.syncSelectedHotbarSlot();
        } else if (!InventoryUtils.setPickedItemToHand(this.shulkerInvSlot, stack, this.client)) {
            abortKeepWorld();
            return;
        }
        this.phase = Phase.PLACE;
        this.settleTicks = 0;
    }

    private void tickPlace(LocalPlayer player) {
        if (this.placementCommitted && isRealPlacedShulker()) {
            selectEmptyHand(player);
            stopExternalWork();
            this.phase = Phase.OPEN;
            this.settleTicks = 0;
            this.openAttempts = 0;
            return;
        }
        if (!isShulker(player.getMainHandItem())) {
            abortKeepWorld();
            return;
        }
        BlockPos target = blockPosAt(player.getX(), player.getY() + PLACE_HEIGHT, player.getZ());
        if (!this.client.level.getBlockState(target).isAir()) {
            target = findNearbyAir(player);
            if (target == null) {
                abortKeepWorld();
                return;
            }
        }
        lookAt(player, target);
        BlockHitResult hit;
        if (!this.client.level.getBlockState(target.below()).isAir()) {
            hit = new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false);
        } else {
            hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.DOWN, target, false);
        }
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
            abortKeepWorld();
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
        selectEmptyHand(player);
        stopExternalWork();
        this.phase = Phase.OPEN;
        this.settleTicks = 0;
        this.openAttempts = 0;
    }

    private void tickOpen(LocalPlayer player) {
        if (this.placedPos == null) {
            abortKeepWorld();
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
                abortKeepWorld();
                return;
            }
            this.placedPos = found;
            this.placementCommitted = true;
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
                retryOpenOrAbort(player);
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
        if (this.openAttempts < MAX_OPEN_ATTEMPTS) {
            this.phase = Phase.OPEN;
            this.settleTicks = 0;
            return;
        }
        if (isRealPlacedShulker()) {
            this.takePhaseDone = true;
            this.phase = Phase.BREAK;
            this.settleTicks = 0;
            return;
        }
        abortKeepWorld();
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
        if (this.activeKind == RefillKind.NETHERITE_PICKAXE) {
            tickTakePickaxeSwap(player);
            return;
        }
        if (this.activeKind == RefillKind.ENDER_CHEST_STACK || this.activeKind == RefillKind.ENCHANTED_GOLDEN_APPLE) {
            tickTakeLimited(player);
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (!menu.getCarried().isEmpty()) {
            if (!returnCarriedToContainer(player, menu)) {
                refreshTookFlag(player);
                this.takePhaseDone = true;
                this.phase = Phase.CLOSE;
                this.settleTicks = 0;
            }
            return;
        }
        if (countEmptySlots(player) <= 1) {
            if (!ensureInventorySpace(player, menu, 2, true, false)) {
                refreshTookFlag(player);
                this.takePhaseDone = true;
                this.phase = Phase.CLOSE;
                this.settleTicks = 0;
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
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.QUICK_MOVE, player);
        //#endif
        this.issuedTakeClick = true;
        this.clickCooldown = CLICK_DELAY;
        refreshTookFlag(player);
        if (!menu.getCarried().isEmpty()) {
            returnCarriedToContainer(player, menu);
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
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
        if (this.pickaxeSwapStage >= 6) {
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
        switch (this.pickaxeSwapStage) {
            case 0 -> {
                if (findEmptyMainInventoryMenuSlot(menu) < 0) {
                    if (!makePickaxeInventorySpace(player, menu)) {
                        this.takePhaseDone = true;
                        this.phase = Phase.CLOSE;
                        this.settleTicks = 0;
                    }
                    return;
                }
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
                clickPickup(menu, player, goodSlot);
                this.pickaxeContainerSlot = goodSlot;
                this.pickaxeSwapStage = 1;
            }
            case 2 -> {
                int worn = resolveTargetPickaxeMenuSlot(menu, player);
                if (worn < 0) {
                    this.pickaxeSwapStage = 4;
                    return;
                }
                clickPickup(menu, player, worn);
                this.pickaxeSwapStage = 3;
            }
            case 4 -> {
                int tempMenu = inventorySlotToMenuSlot(menu, this.pickaxeTempInvSlot);
                if (tempMenu < 0 || tempMenu >= menu.slots.size()
                        || !isUsableNetheritePickaxe(menu.slots.get(tempMenu).getItem())) {
                    tempMenu = findUsablePickaxeMainInventoryMenuSlot(menu);
                }
                if (tempMenu < 0) {
                    this.pickaxeSwapStage = 6;
                    return;
                }
                clickPickup(menu, player, tempMenu);
                this.pickaxeSwapStage = 5;
            }
            default -> this.pickaxeSwapStage = 6;
        }
    }

    private void handlePickaxeSwapCarried(LocalPlayer player, AbstractContainerMenu menu) {
        switch (this.pickaxeSwapStage) {
            case 1 -> {
                int empty = findEmptyMainInventoryMenuSlot(menu);
                if (empty < 0) {
                    if (!makePickaxeInventorySpace(player, menu)) {
                        depositCarriedIntoContainer(player, menu);
                        this.pickaxeSwapStage = 6;
                    }
                    return;
                }
                clickPickup(menu, player, empty);
                this.pickaxeTempInvSlot = menu.slots.get(empty).getContainerSlot();
                this.tookItems = true;
                this.issuedTakeClick = true;
                this.pickaxeSwapStage = 2;
            }
            case 3 -> {
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
                this.pickaxeSwapStage = 4;
            }
            case 5 -> {
                int dest = -1;
                if (this.targetInvSlot >= 0) {
                    dest = inventorySlotToMenuSlot(menu, this.targetInvSlot);
                }
                if (dest < 0 || dest >= menu.slots.size()) {
                    dest = findEmptyHotbarMenuSlot(menu);
                }
                if (dest < 0) {
                    depositCarriedIntoContainer(player, menu);
                    this.pickaxeSwapStage = 6;
                    return;
                }
                clickPickup(menu, player, dest);
                this.issuedTakeClick = true;
                this.tookItems = true;
                this.pickaxeSwapStage = 6;
            }
            default -> {
                depositCarriedIntoContainer(player, menu);
                this.pickaxeSwapStage = 6;
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
        int invSlot = findDisposablePickBlockableHotbarSlot(player, true);
        if (invSlot < 0) {
            return false;
        }
        int menuSlot = inventorySlotToMenuSlot(menu, invSlot);
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return false;
        }
        ItemStack stack = menu.slots.get(menuSlot).getItem();
        if (stack.isEmpty() || isShulker(stack)) {
            return false;
        }
        throwMenuSlot(player, menu, menuSlot);
        this.clickCooldown = CLICK_DELAY;
        return true;
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

    private static int findUsablePickaxeMainInventoryMenuSlot(AbstractContainerMenu menu) {
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
            if (isUsableNetheritePickaxe(slot.getItem())) {
                return i;
            }
        }
        return -1;
    }

    private int resolveTargetPickaxeMenuSlot(AbstractContainerMenu menu, LocalPlayer player) {
        int mapped = inventorySlotToMenuSlot(menu, this.targetInvSlot);
        if (mapped >= 0) {
            ItemStack stack = menu.slots.get(mapped).getItem();
            if (isPickaxeAtOrBelowMinDurability(stack)) {
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
            if (!isPickaxeAtOrBelowMinDurability(stack)) {
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
            if (!isUsableNetheritePickaxe(stack)) {
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
        if (state.requiresCorrectToolForDrops() && (hand.isEmpty() || !hand.isCorrectToolForDrops(state))) {
            InventoryUtils.switchToBestTool(player, state, pos);
            if (RuntimeAccess.get().inventorySwitchGuard().isWaiting()) {
                return false;
            }
            hand = player.getMainHandItem();
            if (hand.isEmpty() || !hand.isCorrectToolForDrops(state)) {
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
        beginEnder(player, Set.of(Items.NETHERITE_PICKAXE));
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
            if (!isShulker(stack) || !shulkerContainsNetheritePickaxe(stack)) {
                continue;
            }
            if (anySlot < 0) {
                anySlot = slot;
            }
            if (!shulkerHasUsablePickaxe(stack) && wornSlot < 0) {
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

    private static int findWornOnlyPickaxeShulker(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack)) {
                continue;
            }
            if (!shulkerContainsNetheritePickaxe(stack)) {
                continue;
            }
            if (!shulkerHasUsablePickaxe(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static int findAnyNetheritePickaxeShulker(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack)) {
                continue;
            }
            if (shulkerContainsNetheritePickaxe(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean shulkerContainsNetheritePickaxe(ItemStack shulker) {
        for (ItemStack inner : listShulkerContents(shulker)) {
            if (isNetheritePickaxe(inner)) {
                return true;
            }
        }
        return false;
    }

    private void tickRestore(LocalPlayer player) {
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
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
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
            Slot slot = menu.slots.get(i);
            if (slot.container instanceof Inventory) {
                continue;
            }
            ItemStack stack = slot.getItem();
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
        try {
            return Math.min(menu.slots.size(), menu.slots.get(0).container.getContainerSize());
        } catch (Exception ignored) {
            int count = 0;
            for (int i = 0; i < menu.slots.size(); i++) {
                if (menu.slots.get(i).container instanceof Inventory) {
                    break;
                }
                count++;
            }
            return count > 0 ? count : Math.min(27, Math.max(0, menu.slots.size() - 36));
        }
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
                    if (this.client.level.getBlockState(pos).isAir()) {
                        return pos;
                    }
                }
            }
        }
        return null;
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

    private boolean ensureInventorySpace(LocalPlayer player, AbstractContainerMenu menu, int minEmpty, boolean allowDepositToContainer, boolean allowNeeded) {
        if (countEmptySlots(player) >= minEmpty) {
            return true;
        }
        int invSlot = findDisposablePickBlockableHotbarSlot(player, allowNeeded);
        if (invSlot < 0) {
            return false;
        }
        int menuSlot = inventorySlotToMenuSlot(menu, invSlot);
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return false;
        }
        if (allowDepositToContainer && tryDepositHotbarStackToContainer(player, menu, menuSlot)) {
            this.clickCooldown = CLICK_DELAY;
            return true;
        }
        throwMenuSlot(player, menu, menuSlot);
        this.clickCooldown = CLICK_DELAY;
        return true;
    }

    private boolean ensureInventorySpaceClosed(LocalPlayer player, int minEmpty) {
        if (countEmptySlots(player) >= minEmpty) {
            return true;
        }
        if (player.containerMenu != player.inventoryMenu || this.client.gameMode == null) {
            return false;
        }
        int invSlot = findDisposablePickBlockableHotbarSlot(player, true);
        if (invSlot < 0) {
            return false;
        }
        int menuSlot = invSlot < 9 ? invSlot + 36 : invSlot;
        ItemStack stack = player.getInventory().getItem(invSlot);
        if (stack.isEmpty() || isShulker(stack)) {
            return false;
        }
        //#if MC > 260100
        this.client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, 1, ContainerInput.THROW, player);
        //#else
        //$$ this.client.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, menuSlot, 1, ClickType.THROW, player);
        //#endif
        return true;
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

    private static boolean canTakeStack(LocalPlayer player, ItemStack source) {
        return countEmptySlots(player) > 1;
    }

    private boolean returnCarriedToContainer(LocalPlayer player, AbstractContainerMenu menu) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() || this.client.gameMode == null) {
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
                return !menu.getCarried().isEmpty();
            }
        }
        return !menu.getCarried().isEmpty();
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
        if (enderRefillEnabled()) {
            int enderSlot = findHotbarEnderChestSlot(player);
            if (enderSlot >= 0) {
                ItemStack stack = player.getInventory().getItem(enderSlot);
                if (stack.getCount() <= 4) {
                    enqueue(RefillKind.ENDER_CHEST_STACK, Set.of(Items.ENDER_CHEST), true);
                }
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_ENCHANTED_GOLDEN_APPLE.getBooleanValue()) {
            int hotbarGapples = countHotbarItem(player, Items.ENCHANTED_GOLDEN_APPLE);
            if (hotbarGapples < 10) {
                enqueue(RefillKind.ENCHANTED_GOLDEN_APPLE, Set.of(Items.ENCHANTED_GOLDEN_APPLE), false);
            }
        }
        if (Configs.Special.MANUAL_VANILLA_REFILL_NETHERITE_PICKAXE.getBooleanValue()) {
            if (findLowDurabilityHotbarPickaxe(player) >= 0
                    || !hasNetheritePickaxeInHotbar(player)) {
                enqueue(RefillKind.NETHERITE_PICKAXE, Set.of(Items.NETHERITE_PICKAXE), true);
            }
        }
    }

    private void tryStartQueued(LocalPlayer player) {
        if (this.phase != Phase.IDLE || player.containerMenu != player.inventoryMenu) {
            return;
        }
        if (isNearAnyWater(player, this.client.level)) {
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
            if (task.kind == RefillKind.NETHERITE_PICKAXE) {
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
            int enderSlot = findHotbarEnderChestSlot(player);
            if (enderSlot < 0) {
                return false;
            }
            ItemStack stack = player.getInventory().getItem(enderSlot);
            int need = Math.max(0, stack.getMaxStackSize() - stack.getCount());
            if (need <= 0) {
                return false;
            }
            this.targetInvSlot = enderSlot;
            this.takeRemaining = need;
        } else if (task.kind == RefillKind.ENCHANTED_GOLDEN_APPLE) {
            int target = findIncompleteHotbarStack(player, Items.ENCHANTED_GOLDEN_APPLE);
            if (target < 0) {
                return false;
            }
            ItemStack stack = player.getInventory().getItem(target);
            int need = Math.max(0, stack.getMaxStackSize() - stack.getCount());
            if (need <= 0) {
                return false;
            }
            this.targetInvSlot = target;
            this.takeRemaining = need;
        } else if (task.kind == RefillKind.NETHERITE_PICKAXE) {
            int lowSlot = findLowDurabilityHotbarPickaxe(player);
            this.targetInvSlot = lowSlot;
            if (lowSlot < 0) {
                if (hasNetheritePickaxeInHotbar(player)) {
                    return false;
                }
                if (findShulkerWithUsablePickaxe(player) < 0
                        && (!enderRefillEnabled() || findEnderChestSlot(player) < 0)) {
                    return false;
                }
            }
        }
        int slot = resolveInventoryShulkerSlot(player, needed);
        if (slot >= 0) {
            begin(player, slot, needed);
            return true;
        }
        return tryStartEnderFetch(player, needed);
    }

    private int resolveInventoryShulkerSlot(LocalPlayer player, Set<Item> needed) {
        if (this.activeKind == RefillKind.NETHERITE_PICKAXE) {
            return findShulkerWithUsablePickaxe(player);
        }
        return findShulkerSlot(player, needed);
    }

    private boolean tryStartEnderFetch(LocalPlayer player, Set<Item> needed) {
        if (!enderRefillEnabled()) {
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
        if (isNearAnyWater(player, this.client.level)) {
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

    private static int findLowDurabilityHotbarPickaxe(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int bestSlot = -1;
        float bestRemaining = 1.0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isPickaxeAtOrBelowMinDurability(stack)) {
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

    private static boolean hasNetheritePickaxeInHotbar(LocalPlayer player) {
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (isNetheritePickaxe(inv.getItem(slot))) {
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

    private int findShulkerWithUsablePickaxe(LocalPlayer player) {
        Inventory inv = player.getInventory();
        int bestSlot = -1;
        int bestUsableCount = Integer.MAX_VALUE;
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!isShulker(stack) || !shulkerHasUsablePickaxe(stack)) {
                continue;
            }
            int usable = countUsableShulkerPickaxes(stack);
            if (usable < bestUsableCount) {
                bestUsableCount = usable;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private static boolean shulkerHasUsablePickaxe(ItemStack shulker) {
        return countUsableShulkerPickaxes(shulker) > 0;
    }

    private static int countUsableShulkerPickaxes(ItemStack shulker) {
        if (!isShulker(shulker)) {
            return 0;
        }
        int count = 0;
        for (ItemStack inner : listShulkerContents(shulker)) {
            if (isUsableNetheritePickaxe(inner)) {
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

    private static boolean isUsableNetheritePickaxe(ItemStack stack) {
        if (!isNetheritePickaxe(stack)) {
            return false;
        }
        return !isPickaxeAtOrBelowMinDurability(stack);
    }

    private static boolean isNetheritePickaxe(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() == Items.NETHERITE_PICKAXE || stack.is(Items.NETHERITE_PICKAXE)) {
            return true;
        }
        try {
            return stack.getItem().toString().contains("netherite_pickaxe");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isBelowDurabilityPercent(ItemStack stack, float threshold) {
        return remainingDurabilityPercent(stack) <= threshold;
    }

    private static boolean isPickaxeAtOrBelowMinDurability(ItemStack stack) {
        if (!isNetheritePickaxe(stack)) {
            return false;
        }
        int max = stackMaxDamage(stack);
        if (max <= 0) {
            max = stack.getMaxDamage();
        }
        if (max <= 0) {
            return false;
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
        int remaining = max - damage;
        return remaining * 100 <= max * 2;
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

    private static boolean isNearAnyWater(LocalPlayer player, net.minecraft.client.multiplayer.ClientLevel level) {
        BlockPos origin = player.blockPosition();
        int radius = 3;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    var fluid = level.getFluidState(pos);
                    if (fluid != null && !fluid.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
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
                abortKeepWorld();
                return;
            }
            this.enderInvSlot = found;
            stack = player.getInventory().getItem(found);
        }
        if (Inventory.isHotbarSlot(this.enderInvSlot)) {
            InventoryUtils.setSelectedSlot(player.getInventory(), this.enderInvSlot);
            InventoryUtils.syncSelectedHotbarSlot();
        } else if (!InventoryUtils.setPickedItemToHand(this.enderInvSlot, stack, this.client)) {
            abortKeepWorld();
            return;
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
                abortKeepWorld();
                return;
            }
            this.enderInvSlot = found;
            ItemStack stack = player.getInventory().getItem(found);
            if (Inventory.isHotbarSlot(found)) {
                InventoryUtils.setSelectedSlot(player.getInventory(), found);
                InventoryUtils.syncSelectedHotbarSlot();
            } else if (!InventoryUtils.setPickedItemToHand(found, stack, this.client)) {
                abortKeepWorld();
                return;
            }
        }
        BlockPos target = resolveEnderPlacePos(player, this.enderPlaceIndex);
        if (target == null) {
            if (!this.placedEnderPositions.isEmpty()) {
                selectEmptyHand(player);
                this.phase = Phase.OPEN_ENDER;
                this.settleTicks = 0;
                this.openAttempts = 0;
                return;
            }
            abortKeepWorld();
            return;
        }
        if (!this.client.level.getBlockState(target).isAir()) {
            if (isRealPlacedEnder(target)) {
                this.placedEnderPositions.add(target);
                this.enderPlaceIndex++;
                this.settleTicks = 0;
                return;
            }
            target = findNearbyAir(player);
            if (target == null || !this.client.level.getBlockState(target).isAir()) {
                if (!this.placedEnderPositions.isEmpty()) {
                    selectEmptyHand(player);
                    this.phase = Phase.OPEN_ENDER;
                    this.settleTicks = 0;
                    this.openAttempts = 0;
                    return;
                }
                abortKeepWorld();
                return;
            }
        }
        lookAt(player, target);
        BlockHitResult hit;
        if (!this.client.level.getBlockState(target.below()).isAir()) {
            hit = new BlockHitResult(Vec3.atCenterOf(target.below()), Direction.UP, target.below(), false);
        } else {
            hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.DOWN, target, false);
        }
        InteractionResult result = vanillaUseItemOn(InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction() || isRealPlacedEnder(target)) {
            this.placedEnderPositions.add(target);
            this.enderPlaceIndex++;
            this.phase = Phase.SETTLE_ENDER;
            this.settleTicks = 0;
        } else if (++this.settleTicks > 20) {
            abortKeepWorld();
        }
    }

    private void tickSettleEnder(LocalPlayer player) {
        if (this.placedEnderPositions.isEmpty()) {
            abortKeepWorld();
            return;
        }
        BlockPos last = this.placedEnderPositions.get(this.placedEnderPositions.size() - 1);
        if (!isRealPlacedEnder(last)) {
            if (++this.settleTicks > PLACE_CONFIRM_TICKS) {
                abortKeepWorld();
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
        selectEmptyHand(player);
        stopExternalWork();
        this.phase = Phase.OPEN_ENDER;
        this.settleTicks = 0;
        this.openAttempts = 0;
    }

    private void tickOpenEnder(LocalPlayer player) {
        BlockPos openPos = primaryEnderPos(player);
        if (openPos == null) {
            abortKeepWorld();
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            this.expectedContainerId = player.containerMenu.containerId;
            this.phase = Phase.WAIT_CONTENT_ENDER;
            this.settleTicks = 0;
            return;
        }
        if (!isRealPlacedEnder(openPos)) {
            abortKeepWorld();
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
                retryOpenEnderOrAbort(player);
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
        if (this.openAttempts < MAX_OPEN_ATTEMPTS) {
            this.phase = Phase.OPEN_ENDER;
            this.settleTicks = 0;
            return;
        }
        if (!this.placedEnderPositions.isEmpty()) {
            this.takePhaseDone = true;
            this.phase = Phase.BREAK_ENDER;
            this.settleTicks = 0;
            this.enderBreakIndex = 0;
            return;
        }
        abortKeepWorld();
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
                this.takePhaseDone = true;
                this.phase = Phase.CLOSE_ENDER;
                this.settleTicks = 0;
            }
            return;
        }
        if (countEmptySlots(player) < 1) {
            if (!ensureInventorySpace(player, menu, 1, false, true)) {
                this.takePhaseDone = true;
                this.phase = Phase.CLOSE_ENDER;
                this.settleTicks = 0;
            }
            return;
        }
        int slot = findShulkerInContainer(menu);
        if (slot < 0) {
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE_ENDER;
            this.settleTicks = 0;
            return;
        }
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
        int size = containerSize(menu);
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
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
            boolean hasPick = false;
            for (ItemStack inner : listShulkerContents(stack)) {
                if (isNetheritePickaxe(inner)) {
                    hasPick = true;
                    break;
                }
            }
            if (hasPick && !shulkerHasUsablePickaxe(stack)) {
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
            for (ItemStack inner : listShulkerContents(stack)) {
                if (isNetheritePickaxe(inner)) {
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
                if (!ensureInventorySpaceClosed(player, 1)) {
                    abortKeepWorld();
                    return;
                }
                this.settleTicks = POST_CLOSE_TICKS;
                return;
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
        if (kind == RefillKind.NETHERITE_PICKAXE) {
            slot = findShulkerWithUsablePickaxe(player);
        } else {
            slot = findShulkerSlot(player, needed);
        }
        if (slot < 0) {
            restoreLook(player);
            RuntimeAccess.get().actionBroker().releaseOwner(LEASE_OWNER);
            stopExternalWork();
            hardClear();
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
            if (this.client.level.getBlockState(side).isAir()) {
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
            return !shulkerContainsNetheritePickaxe(stack);
        });
    }

    private int findShulkerInContainer(AbstractContainerMenu menu) {
        pruneRejectedEnderSlots(menu);
        int size = containerSize(menu);
        int bestSlot = -1;
        int bestUsable = -1;
        int bestItemCount = Integer.MAX_VALUE;
        for (int i = 0; i < size && i < menu.slots.size(); i++) {
            if (this.rejectedEnderPickaxeSlots.contains(i)) {
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
            if (refillKindForShulkerSearch() == RefillKind.NETHERITE_PICKAXE) {
                if (!shulkerContainsNetheritePickaxe(stack)) {
                    continue;
                }
                int usable = countUsableShulkerPickaxes(stack);
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
            if (!containsNeeded(stack, this.neededItems)) {
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

    private enum RefillKind {
        MATERIALS,
        ENDER_CHEST_STACK,
        ENCHANTED_GOLDEN_APPLE,
        NETHERITE_PICKAXE
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
