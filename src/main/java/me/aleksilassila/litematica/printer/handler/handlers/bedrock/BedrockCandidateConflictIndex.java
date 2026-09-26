package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class BedrockCandidateConflictIndex {
    private final Set<BlockPos> structural = new HashSet<>();
    private final Set<BlockPos> power = new HashSet<>();
    private final List<BedrockCandidatePlan> selected = new java.util.ArrayList<>();

    boolean tryReserve(BedrockCandidatePlan candidate) {
        if (intersects(candidate.structuralPositions(), this.structural)
                || intersects(candidate.structuralPositions(), this.power)
                || intersects(candidate.powerReservationPositions(), this.structural)) {
            return false;
        }
        for (BedrockCandidatePlan existing : this.selected) {
            if (candidate.layout() == null || existing.layout() == null) {
                continue;
            }
            if (candidate.placement() != null && existing.placement() != null
                    && sameTorchPlacement(candidate.placement(), existing.placement())) {
                continue;
            }
            if (isTorchPoweredBy(candidate.layout().getPistonPos(), existing.placement())
                    || isTorchPoweredBy(existing.layout().getPistonPos(), candidate.placement())) {
                return false;
            }
        }
        this.structural.addAll(candidate.structuralPositions());
        this.power.addAll(candidate.powerReservationPositions());
        this.selected.add(candidate);
        return true;
    }

    private static boolean intersects(Iterable<BlockPos> positions, Set<BlockPos> occupied) {
        for (BlockPos pos : positions) {
            if (occupied.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameTorchPlacement(BedrockTorchPlacement left, BedrockTorchPlacement right) {
        return left.getClickedFace() == right.getClickedFace()
                && left.getSupportPos() != null
                && left.getSupportPos().equals(right.getSupportPos())
                && left.getTorchPos() != null
                && left.getTorchPos().equals(right.getTorchPos());
    }

    private static boolean isTorchPoweredBy(BlockPos pistonPos, BedrockTorchPlacement placement) {
        return pistonPos != null
                && placement != null
                && placement.getTorchPos() != null
                && BedrockEnvironment.getTorchInfluencePositions(pistonPos).contains(placement.getTorchPos());
    }
}
