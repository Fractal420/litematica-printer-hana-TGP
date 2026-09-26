package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class BedrockSchedulingProbe {
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.EAST,
            Direction.WEST
    };

    private final Minecraft client;
    private final BedrockTargetRegistry targets;
    private final BedrockCleanupCoordinator cleanup;

    BedrockSchedulingProbe(
            Minecraft client,
            BedrockTargetRegistry targets,
            BedrockCleanupCoordinator cleanup
    ) {
        this.client = client;
        this.targets = targets;
        this.cleanup = cleanup;
    }

    int penalty(BlockPos pos) {
        if (pos == null || this.client.level == null
                || (this.targets.isEmpty() && this.cleanup.isEmpty())) {
            return 0;
        }
        int penalty = this.probe(pos) + this.probe(pos.above()) + this.probe(pos.above(2));
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos neighbor = pos.relative(direction);
            penalty += this.probe(neighbor);
            penalty += this.probe(neighbor.above());
        }
        return penalty;
    }

    int predictedMachineOverlapPenalty(
            BlockPos bedrockPos,
            BedrockMachineLayout layout,
            BedrockTorchPlacement placement
    ) {
        if (this.client.level == null || bedrockPos == null || layout == null || this.targets.isEmpty()) {
            return 0;
        }
        return this.targets.predictedMachineOverlapPenalty(bedrockPos, layout, placement);
    }

    private int probe(BlockPos pos) {
        if (pos == null || this.client.level == null) {
            return 0;
        }
        boolean reserved = this.targets.isReserved(pos);
        int penalty = reserved ? 60 : 0;
        var state = this.client.level.getBlockState(pos);
        if (this.cleanup.contains(pos)
                || (!reserved && BedrockTargetBlocks.isCleanupResidue(state))) {
            penalty += this.cleanup.schedulingPenalty(state);
        }
        return penalty;
    }
}
