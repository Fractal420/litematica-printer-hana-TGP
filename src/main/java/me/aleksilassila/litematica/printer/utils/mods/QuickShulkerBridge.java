package me.aleksilassila.litematica.printer.utils.mods;

import me.aleksilassila.litematica.printer.integration.inventory.MaterialRequest;
import me.aleksilassila.litematica.printer.integration.inventory.MaterialReservation;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * Compatibility boundary for the historical quick-shulker implementation.
 *
 * <p>The implementation remains unchanged for now. New code should use this
 * bridge instead of depending on the legacy package directly.</p>
 */
public final class QuickShulkerBridge {
    private static final int PICK_BLOCK_SETTLE_TICKS = 2;
    private static Item pendingPickBlockItem;
    private static int pendingPickBlockTicks;

    private QuickShulkerBridge() {
    }

    public static void requestItem(Item item) {
        if (item != null) {
            requestItem(item, MaterialRequest.Source.OTHER);
        }
    }

    public static MaterialReservation requestItem(Item item, MaterialRequest.Source source) {
        if (item == null) {
            return new MaterialReservation(0L, MaterialReservation.State.UNAVAILABLE);
        }
        return RuntimeAccess.get().materialRequests().request(item, source);
    }

    public static MaterialReservation requestItems(Item[] items, MaterialRequest.Source source) {
        if (items == null || items.length == 0) {
            return new MaterialReservation(0L, MaterialReservation.State.UNAVAILABLE);
        }
        return RuntimeAccess.get().materialRequests().request(items, source);
    }

    /** Handles the optional inventory fallback for both vanilla and Litematica pick-block hooks. */
    public static boolean handlePickBlock(LocalPlayer player, Item item) {
        return handlePickBlock(player, item, false);
    }

    private static boolean handlePickBlock(LocalPlayer player, Item item, boolean easyPlace) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || item == null || item == Items.AIR
                || client.gameMode == null
                || client.gameMode.getPlayerMode() != GameType.SURVIVAL
                || (!easyPlace
                    && !Configs.Core.WORK_SWITCH.getBooleanValue()
                    && !Configs.Placement.QUICK_SHULKER.getBooleanValue())
                || (!Configs.Placement.QUICK_SHULKER.getBooleanValue()
                    && !TakeItOutUtils.isAutoTakeoutEnabled()
                    && !Configs.Special.REMOTE_TAKE.getBooleanValue())
                || player.inventoryMenu.slots.stream().anyMatch(slot -> slot.getItem().is(item))
                // A print/CT request already owns the coordinator. Never turn
                // that unrelated PENDING result into a middle-click intercept.
                || RuntimeAccess.get().materialRequests().isBusy()) {
            return false;
        }

        if (Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            if (pendingPickBlockItem != null) {
                return pendingPickBlockItem == item;
            }
            pendingPickBlockItem = item;
            pendingPickBlockTicks = PICK_BLOCK_SETTLE_TICKS;
            if (easyPlace || !Configs.Core.WORK_SWITCH.getBooleanValue()) {
                RuntimeAccess.get().quickShulkerAdapter().allowExternalRequest();
            }
            return true;
        }

        MaterialReservation reservation = requestItem(item, MaterialRequest.Source.PICK_BLOCK);
        if (reservation.state() == MaterialReservation.State.UNAVAILABLE) {
            return false;
        }
        switchItem();
        return true;
    }

    public static boolean handlePickBlock(LocalPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || ItemStack.isSameItemSameComponents(player.getMainHandItem(), stack)) {
            return false;
        }
        return handlePickBlock(player, stack.getItem());
    }

    public static boolean handleEasyPlacePickBlock(LocalPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()
                || ItemStack.isSameItemSameComponents(player.getMainHandItem(), stack)) {
            return false;
        }
        return handlePickBlock(player, stack.getItem(), true);
    }

    public static boolean switchItem() {
        return Configs.Placement.QUICK_SHULKER.getBooleanValue()
                && RuntimeAccess.get().quickShulkerAdapter().switchItem();
    }

    public static boolean hasPendingRequest() {
        return RuntimeAccess.get().quickShulkerAdapter().hasPendingRequest();
    }

    public static boolean isOpenHandler() {
        return RuntimeAccess.get().quickShulkerAdapter().isOpenHandler();
    }

    public static boolean shouldPause() {
        return RuntimeAccess.get().quickShulkerAdapter().shouldPause();
    }

    public static boolean shouldSuppressContainerScreen(int containerId) {
        return RuntimeAccess.get().quickShulkerAdapter().shouldSuppressContainerScreen(containerId);
    }

    public static void onContainerOpen(int containerId) {
        if (Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            RuntimeAccess.get().quickShulkerAdapter().onContainerOpen(containerId);
        }
    }

    public static void onTick() {
        if (!Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            pendingPickBlockItem = null;
            pendingPickBlockTicks = 0;
            return;
        }
        processPendingPickBlock();
        RuntimeAccess.get().quickShulkerAdapter().tick();
    }

    public static void onInventoryContent() {
        if (Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            RuntimeAccess.get().quickShulkerAdapter().onInventoryContent();
        }
    }

    public static void onMainHandUse(LocalPlayer player) {
        if (Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            RuntimeAccess.get().quickShulkerAdapter().onMainHandUse(player);
        }
    }

    public static void resetRuntime() {
        pendingPickBlockItem = null;
        pendingPickBlockTicks = 0;
        RuntimeAccess.get().quickShulkerAdapter().reset();
    }

    private static void processPendingPickBlock() {
        if (pendingPickBlockItem == null || --pendingPickBlockTicks > 0) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        Item item = pendingPickBlockItem;
        pendingPickBlockItem = null;
        pendingPickBlockTicks = 0;

        if (player == null || client.gameMode == null
                || client.gameMode.getPlayerMode() != GameType.SURVIVAL
                || !Configs.Placement.QUICK_SHULKER.getBooleanValue()) {
            return;
        }
        if (InventoryUtils.playerHasItemInInventory(player, item)) {
            InventoryUtils.setPickedItemToHand(new ItemStack(item), client);
            return;
        }
        requestItem(item, MaterialRequest.Source.PICK_BLOCK);
    }
}
