package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
//#if MC >= 12005
import net.minecraft.core.component.DataComponents;
//#endif
//#if MC >= 12105
import net.minecraft.tags.ItemTags;
//#else
//$$ import net.minecraft.world.item.SwordItem;
//#endif

public final class EatingYieldUtils {
    private static final int SWORD_YIELD_DELAY_TICKS = 8;
    private static long swordHoldStartTick = Long.MIN_VALUE;

    private EatingYieldUtils() {
    }

    public static boolean shouldYield(LocalPlayer player) {
        if (player == null) {
            resetSwordHold();
            return false;
        }
        if (RuntimeAccess.get().manualVanillaRefill().shouldBlockExternalBreaking()) {
            resetSwordHold();
            return true;
        }
        if (player.isUsingItem()) {
            resetSwordHold();
            return true;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (isFood(mainHand)) {
            resetSwordHold();
            return true;
        }
        if (isSword(mainHand)) {
            if (isMiningBlocks()) {
                resetSwordHold();
                return false;
            }
            return swordHoldElapsed();
        }
        resetSwordHold();
        return false;
    }

    private static boolean swordHoldElapsed() {
        long now = RuntimeAccess.get().currentTick();
        if (swordHoldStartTick == Long.MIN_VALUE) {
            swordHoldStartTick = now;
            return false;
        }
        return now - swordHoldStartTick >= SWORD_YIELD_DELAY_TICKS;
    }

    private static void resetSwordHold() {
        swordHoldStartTick = Long.MIN_VALUE;
    }

    private static boolean isMiningBlocks() {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode != null && client.gameMode.isDestroying()) {
            return true;
        }
        return client.options.keyAttack.isDown()
                && client.hitResult instanceof BlockHitResult;
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
