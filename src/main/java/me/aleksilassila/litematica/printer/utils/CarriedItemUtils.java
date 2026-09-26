package me.aleksilassila.litematica.printer.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class CarriedItemUtils {
    private CarriedItemUtils() {
    }

    public static boolean hasCarriedItem(Player player) {
        return player != null
                && player.containerMenu != null
                && !player.containerMenu.getCarried().isEmpty();
    }

    public static boolean tryClearCarried(Minecraft mc) {
        if (mc == null || mc.player == null || mc.gameMode == null) {
            return true;
        }
        LocalPlayer player = mc.player;
        if (!hasCarriedItem(player)) {
            return true;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return false;
        }
        ItemStack carried = player.containerMenu.getCarried();
        Inventory inventory = player.getInventory();
        for (int invSlot = 0; invSlot < 36; invSlot++) {
            ItemStack stack = inventory.getItem(invSlot);
            boolean canDeposit = stack.isEmpty()
                    || ItemStack.isSameItemSameComponents(stack, carried)
                    && stack.getCount() < stack.getMaxStackSize();
            if (!canDeposit) {
                continue;
            }
            int menuSlot = invSlot < 9 ? invSlot + 36 : invSlot;
            //#if MC > 260100
            mc.gameMode.handleContainerInput(
                    player.inventoryMenu.containerId,
                    menuSlot,
                    0,
                    ContainerInput.PICKUP,
                    player
            );
            //#else
            //$$ mc.gameMode.handleInventoryMouseClick(
            //$$         player.inventoryMenu.containerId,
            //$$         menuSlot,
            //$$         0,
            //$$         net.minecraft.world.inventory.ClickType.PICKUP,
            //$$         player
            //$$ );
            //#endif
            if (!hasCarriedItem(player)) {
                return true;
            }
            carried = player.containerMenu.getCarried();
        }
        if (!hasCarriedItem(player)) {
            return true;
        }
        //#if MC > 260100
        mc.gameMode.handleContainerInput(
                player.inventoryMenu.containerId,
                -999,
                0,
                ContainerInput.PICKUP,
                player
        );
        //#else
        //$$ mc.gameMode.handleInventoryMouseClick(
        //$$         player.inventoryMenu.containerId,
        //$$         -999,
        //$$         0,
        //$$         net.minecraft.world.inventory.ClickType.PICKUP,
        //$$         player
        //$$ );
        //#endif
        return !hasCarriedItem(player);
    }
}
