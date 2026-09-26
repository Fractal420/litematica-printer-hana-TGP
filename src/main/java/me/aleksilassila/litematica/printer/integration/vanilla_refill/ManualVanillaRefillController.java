package me.aleksilassila.litematica.printer.integration.vanilla_refill;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.core.action.ResourceLease;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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

    public ManualVanillaRefillController(Minecraft client) {
        this.client = client;
    }

    public void requestItems(Collection<Item> items) {
        if (!enabled() || items == null || items.isEmpty() || isBusy()) {
            return;
        }
        LocalPlayer player = this.client.player;
        if (player == null || this.client.level == null || this.client.gameMode == null) {
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
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
        int slot = findShulkerSlot(player, needed);
        if (slot < 0) {
            return;
        }
        if (isNearAnyWater(player, this.client.level)) {
            this.pendingNeeded.clear();
            this.pendingNeeded.addAll(needed);
            return;
        }
        this.pendingNeeded.clear();
        begin(player, slot, needed);
    }

    public void requestItem(Item item) {
        if (item != null) {
            requestItems(List.of(item));
        }
    }

    public boolean isBusy() {
        return this.phase != Phase.IDLE || !this.pendingNeeded.isEmpty();
    }

    public boolean shouldPause() {
        return enabled() && this.phase != Phase.IDLE;
    }

    public boolean shouldBlockExternalBreaking() {
        return shouldPause() && this.phase != Phase.BREAK && this.phase != Phase.WAIT_BREAK;
    }

    public boolean isAllowedBreakTarget(BlockPos pos) {
        return shouldPause()
                && (this.phase == Phase.BREAK || this.phase == Phase.WAIT_BREAK)
                && this.placedPos != null
                && pos != null
                && this.placedPos.equals(pos);
    }

    public void onContainerOpen(int containerId) {
        if (!isBusy()) {
            return;
        }
        if (this.phase == Phase.OPEN || this.phase == Phase.WAIT_CONTENT || this.phase == Phase.TAKE) {
            this.expectedContainerId = containerId;
        }
    }

    public void onInventoryContent(int containerId) {
        if (!isBusy()) {
            return;
        }
        if (this.phase != Phase.WAIT_CONTENT && this.phase != Phase.TAKE) {
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
                || this.phase == Phase.WAIT_CLOSE;
    }

    public void tick() {
        if (!enabled()) {
            if (this.phase != Phase.IDLE || !this.pendingNeeded.isEmpty()) {
                this.pendingNeeded.clear();
                abortKeepWorld();
            }
            return;
        }
        LocalPlayer player = this.client.player;
        if (player == null || this.client.level == null || this.client.gameMode == null) {
            if (this.phase != Phase.IDLE || !this.pendingNeeded.isEmpty()) {
                this.pendingNeeded.clear();
                abortKeepWorld();
            }
            return;
        }
        if (this.phase == Phase.IDLE) {
            tryStartPending(player);
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
            default -> abortKeepWorld();
        }
    }

    public void reset() {
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
        if (!isShulker(stack) || !containsNeeded(stack, this.neededItems)) {
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
        if (countEmptySlots(player) < 2) {
            refreshTookFlag(player);
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
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
            return;
        }
        if (countEmptySlots(player) < 2) {
            this.takePhaseDone = true;
            this.phase = Phase.CLOSE;
            this.settleTicks = 0;
        }
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
            finishWithoutBreak(player);
            return;
        }
        lookAt(player, this.placedPos);
        continueBreakShulker(player);
        if (++this.settleTicks > 120) {
            this.phase = Phase.RESTORE;
            this.settleTicks = 0;
        }
    }

    private void startBreakShulker(LocalPlayer player) {
        BlockPos pos = this.placedPos;
        if (pos == null || this.client.gameMode == null) {
            return;
        }
        Direction face = Direction.DOWN;
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
        if (++this.settleTicks < POST_BREAK_MOVEMENT_LOCK_TICKS) {
            return;
        }
        this.phase = Phase.RESTORE;
        this.settleTicks = 0;
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
        this.pendingNeeded.clear();
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
        return countEmptySlots(player) >= 2;
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
        int slot = findShulkerSlot(player, needed);
        if (slot < 0) {
            this.pendingNeeded.clear();
            return;
        }
        this.pendingNeeded.clear();
        begin(player, slot, needed);
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
        try {
            for (ItemStack inner : fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(shulker, -1)) {
                if (inner != null && !inner.isEmpty()) {
                    total += inner.getCount();
                }
            }
        } catch (Exception ignored) {
        }
        return total;
    }

    private static boolean containsNeeded(ItemStack shulker, Set<Item> needed) {
        try {
            for (ItemStack inner : fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(shulker, -1)) {
                if (inner.isEmpty()) {
                    continue;
                }
                for (Item item : needed) {
                    if (item != null && (inner.getItem() == item || inner.is(item))) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
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

    private static boolean enabled() {
        return Configs.Special.MANUAL_VANILLA_REFILL.getBooleanValue();
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
        RESTORE
    }
}
