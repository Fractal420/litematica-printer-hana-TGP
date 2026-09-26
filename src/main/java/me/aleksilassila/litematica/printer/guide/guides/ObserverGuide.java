package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.*;

public class ObserverGuide extends Guide {

    public ObserverGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, ObserverBlock.FACING).orElseThrow();

        if (!Configs.Print.SAFELY_OBSERVER.getBooleanValue()) {
            return Result.success(new Action()
                    .setLookDirection(facing)
                    .setNeedWaitModifyLook());
        }

        SchematicBlockContext input = context.offset(facing);
        SchematicBlockContext output = context.offset(facing.getOpposite());

        List<Property<?>> inputPropertiesToIgnore = new ArrayList<>();
        if (input.requiredState.getBlock() instanceof WallBlock) {
            BlockStateUtils.getWallFacingProperty(facing.getOpposite()).ifPresent(inputPropertiesToIgnore::add);
        }
        if (input.requiredState.getBlock() instanceof CrossCollisionBlock) {
            BlockStateUtils.getCrossCollisionBlock(facing.getOpposite()).ifPresent(inputPropertiesToIgnore::add);
        }

        BlockMatchResult inputState = BlockMatchResult.compare(input, inputPropertiesToIgnore.toArray(new Property<?>[0]));
        BlockMatchResult outputState = BlockMatchResult.compare(output);

        if (inputState == BlockMatchResult.CORRECT && outputState == BlockMatchResult.CORRECT) {
            if (!isObserverInputChainReady(input)) {
                return Result.SKIP;
            }
            return Result.success(placementAction(facing));
        }

        if (inputState == BlockMatchResult.CORRECT) {

            SchematicBlockContext temp = input;
            while (temp.requiredState.getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
                SchematicBlockContext offset = temp.offset(Direction.DOWN);
                if (BlockMatchResult.compare(offset) != BlockMatchResult.CORRECT) {
                    return Result.SKIP;
                }
                temp = offset;
            }
            if (!isObserverInputChainReady(input)) {
                return Result.SKIP;
            }

            for (Direction direction : Direction.values()) {
                SchematicBlockContext offset = output.offset(direction);
                if (offset.blockPos.equals(output.blockPos)) continue;
                if (offset.blockPos.equals(input.blockPos)) continue;
                if (offset.blockPos.equals(blockPos)) continue;
                if (offset.requiredState.getBlock() instanceof PistonBaseBlock) {
                    if (!offset.currentState.isAir()) {
                        return Result.SKIP;
                    }
                }
            }
        } else if (inputState == BlockMatchResult.WRONG_STATE) {
            return Result.SKIP;
        } else {
            if (!output.requiredState.isAir()) {
                if (output.currentState.isAir() && input.requiredState.getBlock() instanceof WallBlock) {
                    return Result.success(placementAction(facing).setCooldownTicksOverride(2));
                }
                return Result.SKIP;
            } else {

                return Result.SKIP;
            }
        }

        return Result.success(placementAction(facing));
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        return Result.SKIP;
    }

    private static Action placementAction(Direction facing) {
        return new Action()
                .setLookDirection(facing)
                .setNeedWaitModifyLook();
    }

    private static boolean isObserverInputChainReady(SchematicBlockContext start) {
        Set<BlockPos> visited = new HashSet<>();
        SchematicBlockContext temp = start;
        while (temp.requiredState.getBlock() instanceof ObserverBlock) {
            if (!visited.add(temp.blockPos)) {
                return true;
            }
            Direction tempFacing = BlockStateUtils.getProperty(temp.requiredState, ObserverBlock.FACING).orElse(null);
            if (tempFacing == null) {
                return false;
            }
            SchematicBlockContext offset = temp.offset(tempFacing);
            if (BlockMatchResult.compare(offset) != BlockMatchResult.CORRECT) {
                return false;
            }
            temp = offset;
        }
        return true;
    }
}
