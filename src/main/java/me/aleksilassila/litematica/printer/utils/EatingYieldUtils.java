package me.aleksilassila.litematica.printer.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
//#if MC >= 12005
import net.minecraft.core.component.DataComponents;
//#endif

public final class EatingYieldUtils {
    private EatingYieldUtils() {
    }

    public static boolean shouldYield(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.isUsingItem()) {
            return true;
        }
        return isFood(player.getMainHandItem());
    }

    public static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        //#if MC >= 12005
        return stack.get(DataComponents.FOOD) != null;
        //#else
        //$$ return stack.getItem().isEdible();
        //#endif
    }
}
