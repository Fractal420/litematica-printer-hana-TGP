package me.aleksilassila.litematica.printer.guide;

import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;

import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

public class DefaultGuide extends Guide {

    public DefaultGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Action action = new Action();

        Direction facing = getProperty(requiredState, BlockStateProperties.FACING)
                .or(() -> getProperty(requiredState, BlockStateProperties.HORIZONTAL_FACING))
                .or(() -> getProperty(requiredState, BlockStateProperties.VERTICAL_DIRECTION))
                .or(() -> getProperty(requiredState, BlockStateProperties.FACING_HOPPER))
                .orElse(null);
        Direction.Axis axis = getProperty(requiredState, BlockStateProperties.AXIS)
                .or(() -> getProperty(requiredState, BlockStateProperties.HORIZONTAL_AXIS))
                .orElse(null);
        Half half = getProperty(requiredState, BlockStateProperties.HALF).orElse(null);
        AttachFace attachFace = getProperty(requiredState, BlockStateProperties.ATTACH_FACE).orElse(null);

        if (requiredBlock instanceof FaceAttachedHorizontalDirectionalBlock && facing != null && attachFace != null) {
            Direction sidePitch = attachFace == AttachFace.CEILING ? Direction.UP
                    : attachFace == AttachFace.FLOOR ? Direction.DOWN
                    : facing;
            Direction clickSide = attachFace == AttachFace.WALL ? facing : facing.getOpposite();
            return Result.success(action
                    .setSides(clickSide)
                    .setLookDirection(clickSide.getOpposite(), sidePitch)
                    .setNeedWaitModifyLook());
        }

        if (axis != null) {
            action.setSides(axis);
        }

        if (facing != null && axis == null) {

            if (requiredBlock instanceof HorizontalDirectionalBlock
                    || requiredBlock instanceof StonecutterBlock
                    //#if MC >= 12105
                    || requiredBlock instanceof FlowerBedBlock
                    //#endif
            ) {

                action.setLookDirection(facing.getOpposite());
            }

            if (requiredBlock instanceof BaseEntityBlock) {
                if (requiredState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    Direction entityFacing = facing;
                    //#if MC >= 11904
                    if (requiredBlock instanceof DecoratedPotBlock || requiredBlock instanceof CampfireBlock) {
                        entityFacing = entityFacing.getOpposite();
                    }
                    //#endif
                    action.setSides(entityFacing).setLookDirection(entityFacing.getOpposite());
                }
                if (requiredState.hasProperty(BlockStateProperties.FACING)) {
                    Direction entityFacing = facing;
                    if (requiredBlock instanceof ShulkerBoxBlock) {
                        entityFacing = entityFacing.getOpposite();
                        action.setShift();
                    }
                    if (requiredBlock instanceof BarrelBlock || requiredBlock instanceof DispenserBlock) {
                        action.setNeedWaitModifyLook();
                    }
                    action.setSides(entityFacing).setLookDirection(entityFacing.getOpposite());
                }
            }

            if (requiredBlock instanceof ObserverBlock
                    || requiredBlock instanceof StairBlock
                    || requiredBlock instanceof FenceGateBlock) {
                action.setLookDirection(facing);
            } else if (!(requiredBlock instanceof HorizontalDirectionalBlock)
                    && !(requiredBlock instanceof BaseEntityBlock)) {

                action.setLookDirection(facing.getOpposite());
            }
        }

        if (half != null && facing == null) {
            action.setSides(half == Half.BOTTOM
                    ? Direction.DOWN : Direction.UP);
        }

        return Result.success(action);
    }

    @Override
    protected Result onBuildActionWrongBlock(BlockMatchResult state) {
        boolean printBreakWrongBlock = Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue();
        boolean printBreakExtraBlock = Configs.Print.BREAK_EXTRA_BLOCK.getBooleanValue();
        if ((printBreakWrongBlock || printBreakExtraBlock)
                && !RuntimeAccess.get().manualVanillaRefill().shouldPause()) {
            if (ConfigUtils.isPositionInMineSelectionRange(client.player, blockPos)
                    && InteractionUtils.canBreakBlock(blockPos)
                    && InteractionUtils.breakRestriction(currentState)) {
                if (printBreakWrongBlock && !requiredState.isAir()) {
                    InteractionUtils.getRuntime().add(context);
                } else if (printBreakExtraBlock && requiredState.isAir()) {
                    InteractionUtils.getRuntime().add(context);
                }
            }
        }
        return Result.PASS;
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        if (!Configs.Print.BREAK_WRONG_STATE_BLOCK.getBooleanValue() || shouldIgnoreWrongStateBreak()) {
            return Result.PASS;
        }
        if (ConfigUtils.isPositionInMineSelectionRange(client.player, blockPos)
                && InteractionUtils.canBreakBlock(blockPos)
                && InteractionUtils.breakRestriction(currentState)) {
            InteractionUtils.getRuntime().add(context);
        }
        return Result.PASS;
    }

    private boolean shouldIgnoreWrongStateBreak() {
        return requiredState.hasProperty(BlockStateProperties.UP)
                || requiredState.hasProperty(BlockStateProperties.DOWN)
                || requiredState.hasProperty(BlockStateProperties.NORTH)
                || requiredState.hasProperty(BlockStateProperties.EAST)
                || requiredState.hasProperty(BlockStateProperties.SOUTH)
                || requiredState.hasProperty(BlockStateProperties.WEST)
                || Reference.isIgnoreWrongStateBlock(requiredBlock);
    }
}
