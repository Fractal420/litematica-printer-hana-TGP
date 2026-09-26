package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;

import java.util.List;

record BedrockCandidatePlan(
        BlockPos pos,
        BedrockMachineLayout layout,
        BedrockTorchPlacement placement,
        BlockPos slimePos,
        List<BlockPos> structuralPositions,
        List<BlockPos> powerReservationPositions,
        int priority,
        int neighborTargetCount,
        PrinterBox footprint,
        long planRevision
) {
    BedrockCandidatePlan withPlanRevision(long revision) {
        return new BedrockCandidatePlan(
                this.pos,
                this.layout,
                this.placement,
                this.slimePos,
                this.structuralPositions,
                this.powerReservationPositions,
                this.priority,
                this.neighborTargetCount,
                this.footprint,
                revision
        );
    }
}
