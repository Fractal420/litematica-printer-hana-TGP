package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.EatingYieldUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.PlayerUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.ToolSelectionUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolInventorySelector {
    private ToolInventorySelector() {
    }

    public static boolean switchToBestTool(Minecraft client, LocalPlayer player, BlockState blockState) {
        ClientLevel level = client == null ? null : client.level;
        BlockPos pos = player == null ? null : player.blockPosition();
        return switchToBestTool(client, player, blockState, level, pos);
    }

    public static boolean switchToBestTool(
            Minecraft client,
            LocalPlayer player,
            BlockState blockState,
            ClientLevel level,
            BlockPos pos
    ) {
        if (client == null || player == null || blockState == null || blockState.isAir()) {
            return false;
        }
        if (RuntimeAccess.get().inventorySwitchGuard().isWaiting()
                || PlayerUtils.getAbilities(player).instabuild
                || EatingYieldUtils.shouldYield(player)) {
            return false;
        }

        ItemStack currentStack = player.getMainHandItem();
        float bestProgress = destroyProgress(player, level, pos, blockState, currentStack);
        boolean preferSilkTouch = ToolSelectionUtils.prefersSilkTouchForDrops(blockState);
        boolean bestHasSilkTouch = preferSilkTouch && ToolSelectionUtils.hasSilkTouch(currentStack);
        int bestSlot = -1;
        ItemStack bestStack = ItemStack.EMPTY;

        NonNullList<ItemStack> stacks = InventoryUtils.getMainStacks(player.getInventory());
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float progress = destroyProgress(player, level, pos, blockState, stack);
            boolean stackHasSilkTouch = preferSilkTouch && ToolSelectionUtils.hasSilkTouch(stack);
            if ((stackHasSilkTouch && !bestHasSilkTouch)
                    || stackHasSilkTouch == bestHasSilkTouch && progress > bestProgress) {
                bestProgress = progress;
                bestHasSilkTouch = stackHasSilkTouch;
                bestSlot = slot;
                bestStack = stack;
            }
        }

        if (bestSlot == -1 || bestStack.isEmpty()
                || !InventoryUtils.setPickedItemToHand(bestSlot, bestStack, client)) {
            return false;
        }
        if (player != null && !player.getMainHandItem().is(bestStack.getItem())) {
            RuntimeAccess.get().inventorySwitchGuard().markSwitchIfNeeded(bestStack);
            return false;
        }
        return true;
    }

    private static float destroyProgress(
            LocalPlayer player,
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack stack
    ) {
        float hardness = level == null || pos == null
                ? state.getBlock().defaultDestroyTime()
                : state.getDestroySpeed(level, pos);
        if (hardness < 0.0F) return 0.0F;
        if (hardness == 0.0F) return 1.0F;
        int divisor = (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)) ? 30 : 100;
        return PlayerUtils.getBlockBreakingSpeed(player, state, stack) / hardness / (float) divisor;
    }
}
