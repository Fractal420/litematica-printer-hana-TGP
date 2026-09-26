package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class DoorGuide extends Guide {

    private final @Nullable DoorHingeSide doorHinge;

    private final @Nullable DoubleBlockHalf doubleBlockHalf;

    public DoorGuide(SchematicBlockContext context) {
        super(context);
        this.doorHinge = getProperty(requiredState, BlockStateProperties.DOOR_HINGE).orElse(null);
        this.doubleBlockHalf = getProperty(requiredState, BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, DoorBlock.FACING).orElse(null);
        if (facing == null || doorHinge == null || doubleBlockHalf == null) return Result.PASS;

        if (doubleBlockHalf == DoubleBlockHalf.UPPER) return Result.PASS;

        BlockPos upperPos = blockPos.above();

        Direction hingeSide = facing.getCounterClockWise();
        double offset = doorHinge == DoorHingeSide.RIGHT ? 0.25 : -0.25;
        Vec3 hingeVec = facing.getAxis() == Direction.Axis.X
                ? new Vec3(0, 0, offset)
                : new Vec3(offset, 0, 0);

        Map<Direction, Vec3> sides = new HashMap<>();
        sides.put(hingeSide, Vec3.ZERO);
        sides.put(Direction.DOWN, hingeVec);
        sides.put(facing, hingeVec);

        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();
        BlockState leftState = level.getBlockState(blockPos.relative(left));
        BlockState leftUpperState = level.getBlockState(upperPos.relative(left));
        BlockState rightState = level.getBlockState(blockPos.relative(right));
        BlockState rightUpperState = level.getBlockState(upperPos.relative(right));

        int occupancy = (leftState.isCollisionShapeFullBlock(level, blockPos.relative(left)) ? -1 : 0)
                + (leftUpperState.isCollisionShapeFullBlock(level, upperPos.relative(left)) ? -1 : 0)
                + (rightState.isCollisionShapeFullBlock(level, blockPos.relative(right)) ? 1 : 0)
                + (rightUpperState.isCollisionShapeFullBlock(level, upperPos.relative(right)) ? 1 : 0);

        boolean isLeftDoor = leftState.getBlock() instanceof DoorBlock
                && getProperty(leftState, BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null) == DoubleBlockHalf.LOWER;
        boolean isRightDoor = rightState.getBlock() instanceof DoorBlock
                && getProperty(rightState, BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null) == DoubleBlockHalf.LOWER;

        boolean canPlace = (doorHinge == DoorHingeSide.RIGHT && ((isLeftDoor && !isRightDoor) || occupancy > 0))
                || (doorHinge == DoorHingeSide.LEFT && ((isRightDoor && !isLeftDoor) || occupancy < 0))
                || (occupancy == 0 && (isLeftDoor == isRightDoor));

        return Result.resultIf(canPlace,
                new Action().setSides(sides).setLookDirection(facing).setRequiresSupport());
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {

        if (requiredState.is(Blocks.IRON_DOOR) || requiredState.is(Blocks.IRON_TRAPDOOR)) {
            return Result.SKIP;
        }

        if (!getProperty(requiredState, BlockStateProperties.OPEN).equals(getProperty(currentState, BlockStateProperties.OPEN))) {
            return Result.success(new ClickAction());
        }
        return Result.SKIP;
    }
}
