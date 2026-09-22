package me.aleksilassila.litematica.printer.utils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
//#if MC >= 12005
import net.minecraft.core.component.DataComponents;
//#endif
//#if MC >= 12105
import net.minecraft.tags.ItemTags;
//#else
//$$ import net.minecraft.world.item.SwordItem;
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
        ItemStack mainHand = player.getMainHandItem();
        if (isFood(mainHand)) {
            return true;
        }
        return isSword(mainHand);
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

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        //#if MC >= 12105
        return stack.is(ItemTags.SWORDS);
        //#else
        //$$ return stack.getItem() instanceof SwordItem;
        //#endif
    }
}
