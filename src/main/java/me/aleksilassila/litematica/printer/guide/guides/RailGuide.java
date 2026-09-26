package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RailGuide extends Guide {
    private static final int MAX_REPAIR_ATTEMPTS = 3;
    private static final int PENDING_REPAIR_TICKS = 40;
    private static final Map<RailRepairKey, Integer> repairAttempts = new HashMap<>();
    private static final Map<RailRepairKey, Long> pendingRepairs = new HashMap<>();

    public RailGuide(SchematicBlockContext context) {
        super(context);
    }

    public static void clearRepairState() {
        repairAttempts.clear();
        pendingRepairs.clear();
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        clearPendingRepairState(blockPos);

        Optional<RailShape> railShape = getRailShape(requiredState);

        if (railShape.isEmpty()) return Result.PASS;

        Action action = new Action();
        switch (railShape.get()) {
            case EAST_WEST, ASCENDING_EAST -> action.setLookDirection(Direction.EAST);
            case NORTH_SOUTH, ASCENDING_NORTH -> action.setLookDirection(Direction.NORTH);
            case ASCENDING_WEST -> action.setLookDirection(Direction.WEST);
            case ASCENDING_SOUTH -> action.setLookDirection(Direction.SOUTH);
            default -> {
            }
        }
        return Result.success(action);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        if (!Configs.Print.REPAIR_RAIL_SHAPE.getBooleanValue()) {
            return Result.SKIP;
        }

        Optional<RailShape> requiredShape = getRailShape(requiredState);
        Optional<RailShape> currentShape = getRailShape(currentState);
        if (requiredShape.isEmpty() || currentShape.isEmpty() || requiredShape.equals(currentShape)) {
            return Result.SKIP;
        }

        RailRepairKey key = repairKey(requiredShape.get());
        if (!railConnectionsReady(requiredShape.get())
                || isRepairPending(key)
                || repairAttempts.getOrDefault(key, 0) >= MAX_REPAIR_ATTEMPTS
                || !InteractionUtils.canBreakBlock(blockPos)
                || !InteractionUtils.breakRestriction(currentState)) {
            return Result.SKIP;
        }

        repairAttempts.put(key, repairAttempts.getOrDefault(key, 0) + 1);
        pendingRepairs.put(key, level.getGameTime());
        InteractionUtils.getRuntime().add(key.pos());
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionCorrect(BlockMatchResult state) {
        clearPendingRepairState(blockPos);
        return Result.PASS;
    }

    private Optional<RailShape> getRailShape(BlockState state) {
        return getProperty(state, BlockStateProperties.RAIL_SHAPE)
                .or(() -> getProperty(state, BlockStateProperties.RAIL_SHAPE_STRAIGHT));
    }

    private boolean isRepairPending(RailRepairKey key) {
        Long startedAt = pendingRepairs.get(key);
        if (startedAt == null) {
            return false;
        }
        if (level.getGameTime() - startedAt < PENDING_REPAIR_TICKS) {
            return true;
        }
        pendingRepairs.remove(key);
        return false;
    }

    private void clearRepairState(BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        repairAttempts.keySet().removeIf(key -> key.matches(dimension, pos));
        pendingRepairs.keySet().removeIf(key -> key.matches(dimension, pos));
    }

    private void clearPendingRepairState(BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        pendingRepairs.keySet().removeIf(key -> key.matches(dimension, pos));
    }

    private RailRepairKey repairKey(RailShape shape) {
        return new RailRepairKey(level.dimension(), blockPos.immutable(), shape);
    }

    private boolean railConnectionsReady(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> connectionReady(blockPos.north()) && connectionReady(blockPos.south());
            case EAST_WEST -> connectionReady(blockPos.west()) && connectionReady(blockPos.east());
            case ASCENDING_EAST -> connectionReady(blockPos.west()) && connectionReady(blockPos.east().above());
            case ASCENDING_WEST -> connectionReady(blockPos.west().above()) && connectionReady(blockPos.east());
            case ASCENDING_NORTH -> connectionReady(blockPos.north().above()) && connectionReady(blockPos.south());
            case ASCENDING_SOUTH -> connectionReady(blockPos.north()) && connectionReady(blockPos.south().above());
            case SOUTH_EAST -> connectionReady(blockPos.south()) && connectionReady(blockPos.east());
            case SOUTH_WEST -> connectionReady(blockPos.south()) && connectionReady(blockPos.west());
            case NORTH_WEST -> connectionReady(blockPos.north()) && connectionReady(blockPos.west());
            case NORTH_EAST -> connectionReady(blockPos.north()) && connectionReady(blockPos.east());
        };
    }

    private boolean connectionReady(BlockPos pos) {
        BlockState schematicState = schematic.getBlockState(pos);
        return !BaseRailBlock.isRail(schematicState) || BaseRailBlock.isRail(level.getBlockState(pos));
    }

    private record RailRepairKey(ResourceKey<Level> dimension, BlockPos pos, RailShape requiredShape) {
        private boolean matches(ResourceKey<Level> dimension, BlockPos pos) {
            return this.dimension.equals(dimension) && this.pos.equals(pos);
        }
    }
}
