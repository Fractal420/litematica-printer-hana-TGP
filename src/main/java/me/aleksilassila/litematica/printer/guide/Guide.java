package me.aleksilassila.litematica.printer.guide;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.minecraft.BlockStateUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

public abstract class Guide extends BlockStateUtils {
    protected final SchematicBlockContext context;
    public final Minecraft client;
    public final ClientLevel level;
    public final WorldSchematic schematic;
    public final BlockPos blockPos;
    public final BlockState currentState;
    public final BlockState requiredState;
    protected final Block currentBlock;
    protected final Block requiredBlock;

    public Guide(SchematicBlockContext context) {
        this.context = context;
        this.client = context.client;
        this.level = context.level;
        this.schematic = context.schematic;
        this.blockPos = context.blockPos;
        this.currentBlock = context.currentState.getBlock();
        this.requiredBlock = context.requiredState.getBlock();
        this.currentState = context.currentState;
        this.requiredState = context.requiredState;
    }

    public final Result buildAction(BlockMatchResult state) {

        if (state == BlockMatchResult.CORRECT) {
            return this.onBuildActionCorrect(state);
        }

        if (state == BlockMatchResult.MISSING) {

            if (!BlockStateUtils.isWaterBlock(requiredState) && !requiredState.canSurvive(level, blockPos)) {
                return Result.PASS;
            }

            if (requiredState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && requiredState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return Result.PASS;
            }
        }

        if (BlockStateUtils.requiresWaterToPlace(requiredBlock)) {
            if (!BlockStateUtils.hasSourceWaterFluid(level.getBlockState(blockPos))) {
                return Result.PASS;
            }
        }

        Result result = this.onBuildAction(state);
        if (!result.passToNext() || result.skipOtherGuide()) {
            return result;
        }

        return switch (state) {
            case MISSING -> this.onBuildActionMissingBlock(state);
            case WRONG_BLOCK -> this.onBuildActionWrongBlock(state);
            case WRONG_STATE -> this.onBuildActionWrongState(state);
            default -> Result.PASS;
        };
    }

    protected boolean canExecute() {
        return true;
    }

    protected Result onBuildAction(BlockMatchResult state) {
        return Result.PASS;
    }

    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        return Result.PASS;
    }

    protected Result onBuildActionWrongBlock(BlockMatchResult state) {
        return Result.PASS;
    }

    protected Result onBuildActionWrongState(BlockMatchResult state) {
        return Result.PASS;
    }

    protected Result onBuildActionCorrect(BlockMatchResult state) {
        return Result.PASS;
    }

}
