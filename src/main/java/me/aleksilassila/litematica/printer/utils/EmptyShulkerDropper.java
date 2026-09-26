package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;

public final class EmptyShulkerDropper {
    private static final int SCAN_INTERVAL_TICKS = 60;
    private static long lastScanTick = Long.MIN_VALUE / 2;

    private EmptyShulkerDropper() {
    }

    public static void tick(Minecraft mc) {
        if (!Configs.Special.DROP_EMPTY_SHULKERS.getBooleanValue()) {
            return;
        }
        if (!Configs.Core.WORK_SWITCH.getBooleanValue()) {
            return;
        }
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        if (RuntimeAccess.get().manualVanillaRefill().isBusy()) {
            return;
        }
        long now = RuntimeAccess.get().currentTick();
        if (now == Long.MIN_VALUE) {
            return;
        }
        if (lastScanTick != Long.MIN_VALUE / 2 && now - lastScanTick < SCAN_INTERVAL_TICKS) {
            return;
        }
        lastScanTick = now;
        Inventory inv = player.getInventory();
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack stack = inv.getItem(invSlot);
            if (!isEmptyShulkerBox(stack)) {
                continue;
            }
            throwInventorySlot(mc, player, invSlot);
        }
    }

    private static void throwInventorySlot(Minecraft mc, LocalPlayer player, int invSlot) {
        int menuSlot = invSlot < 9 ? invSlot + 36 : invSlot;
        //#if MC > 260100
        mc.gameMode.handleContainerInput(
                player.inventoryMenu.containerId,
                menuSlot,
                1,
                ContainerInput.THROW,
                player
        );
        //#else
        //$$ mc.gameMode.handleInventoryMouseClick(
        //$$         player.inventoryMenu.containerId,
        //$$         menuSlot,
        //$$         1,
        //$$         net.minecraft.world.inventory.ClickType.THROW,
        //$$         player
        //$$ );
        //#endif
    }

    private static boolean isShulkerBox(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return block instanceof ShulkerBoxBlock;
    }

    private static boolean isEmptyShulkerBox(ItemStack stack) {
        if (!isShulkerBox(stack)) {
            return false;
        }
        try {
            List<ItemStack> items = fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(stack, -1);
            if (items == null || items.isEmpty()) {
                return true;
            }
            for (ItemStack inner : items) {
                if (inner != null && !inner.isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
