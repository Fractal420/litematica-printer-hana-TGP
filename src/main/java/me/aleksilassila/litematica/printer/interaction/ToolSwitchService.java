package me.aleksilassila.litematica.printer.interaction;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.integration.tweakeroo.TweakerooToolSwitchPort;
import me.aleksilassila.litematica.printer.utils.EatingYieldUtils;
import me.aleksilassila.litematica.printer.utils.InventorySwitchGuard;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolSwitchService {
    private final Minecraft client;
    private final TweakerooToolSwitchPort tweakeroo;
    private final InventorySwitchGuard switchGuard;

    public ToolSwitchService(
            Minecraft client,
            TweakerooToolSwitchPort tweakeroo,
            InventorySwitchGuard switchGuard
    ) {
        this.client = client;
        this.tweakeroo = tweakeroo;
        this.switchGuard = switchGuard;
    }

    public ToolPreparationResult prepareForBreak(BlockPos pos, BlockState state, boolean allowEffectiveSwitch) {
        LocalPlayer player = this.client.player;
        if (player == null || player.getAbilities().instabuild || pos == null || state == null
                || state.isAir() || state.getBlock() instanceof LiquidBlock) {
            return player == null ? ToolPreparationResult.UNAVAILABLE : ToolPreparationResult.READY;
        }
        if (this.switchGuard.isWaiting()) {
            return ToolPreparationResult.SWITCHED_WAITING_SYNC;
        }
        if (EatingYieldUtils.shouldYield(player)) {
            return ToolPreparationResult.UNAVAILABLE;
        }

        int beforeSlot = InventoryUtils.getSelectedSlot(player.getInventory());
        ItemStack before = player.getMainHandItem().copy();
        if (allowEffectiveSwitch) {
            if (this.tweakeroo.isEffectiveToolSwitchEnabled()) {
                this.tweakeroo.switchToEffectiveTool(pos);
            } else if (Configs.Break.BREAK_AUTO_TOOL.getBooleanValue()) {
                InventoryUtils.switchToBestTool(player, state, pos);
            }
        }
        if (this.tweakeroo.isDurabilityGuardActive()
                && !this.tweakeroo.prepareCurrentTool(pos, state)) {
            return ToolPreparationResult.BLOCKED_BY_DURABILITY;
        }
        int afterSlot = InventoryUtils.getSelectedSlot(player.getInventory());
        if (beforeSlot != afterSlot || stackFingerprintChanged(before, player.getMainHandItem())) {
            this.switchGuard.markSwitchIfNeeded(player.getMainHandItem());
            return ToolPreparationResult.SWITCHED_WAITING_SYNC;
        }
        return ToolPreparationResult.READY;
    }

    public boolean isDurabilityGuardActive() {
        return this.tweakeroo.isDurabilityGuardActive();
    }

    private static boolean stackFingerprintChanged(ItemStack before, ItemStack after) {
        if (before.isEmpty() || after.isEmpty()) {
            return before.isEmpty() != after.isEmpty();
        }
        return before.getItem() != after.getItem()
                || before.getDamageValue() != after.getDamageValue()
                || before.getCount() != after.getCount();
    }
}
