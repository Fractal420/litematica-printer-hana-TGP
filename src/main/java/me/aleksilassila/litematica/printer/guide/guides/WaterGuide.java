package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 水源/含水方块的无状态兜底规则。
 * 破冰放水这类跨 tick 流程由 PrintWorkflowScheduler 接管。
 */
public class WaterGuide extends Guide {
    public WaterGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected boolean canExecute() {
        return BlockStateUtils.isWaterBlock(requiredState);
    }

    @Override
    protected Result onBuildAction(BlockMatchResult state) {
        if (shouldSkipWaterloggedTarget()) {
            return Result.SKIP;
        }
        if (state == BlockMatchResult.WRONG_BLOCK
                && Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
                && !(currentState.getBlock() instanceof LiquidBlock)) {
            if (ConfigUtils.isPositionInMineSelectionRange(client.player, blockPos)
                    && InteractionUtils.canBreakBlock(blockPos)
                    && InteractionUtils.breakRestriction(currentState)) {
                InteractionUtils.getRuntime().add(context);
            }
            return Result.SKIP;
        }
        if (isWaterloggedTarget()) {
            return Result.PASS;
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionCorrect(BlockMatchResult state) {
        return isWaterloggedTarget() ? Result.PASS : Result.SKIP;
    }

    private boolean shouldSkipWaterloggedTarget() {
        return Configs.Print.SKIP_WATERLOGGED_BLOCK.getBooleanValue() && isWaterloggedTarget();
    }

    private boolean isWaterloggedTarget() {
        return requiredState.hasProperty(BlockStateProperties.WATERLOGGED)
                && requiredState.getValue(BlockStateProperties.WATERLOGGED);
    }
}
