package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

final class BedrockSubmissionPlanCache {
    private final Map<BlockPos, Plan> plans = new HashMap<>();

    void clear() {
        this.plans.clear();
    }

    void put(
            BlockPos bedrockPos,
            BedrockMachineLayout layout,
            BedrockTorchPlacement placement,
            BlockPos slimePos,
            long tick
    ) {
        BlockPos stablePos = stablePos(bedrockPos);
        if (stablePos != null && layout != null) {
            this.plans.put(stablePos, new Plan(layout, placement, slimePos, tick));
        }
    }

    Plan consume(BlockPos pos, long tick) {
        if (pos == null) {
            return null;
        }
        Plan plan = this.plans.remove(pos);
        return plan != null && tick - plan.plannedAtTick() <= 1L ? plan : null;
    }

    Boolean horizontal(BlockPos pos) {
        Plan plan = this.plans.get(pos);
        return plan == null || plan.layout() == null ? null : plan.layout().isHorizontal();
    }

    private static BlockPos stablePos(BlockPos pos) {
        return pos == null ? null : pos.immutable();
    }

    record Plan(
            BedrockMachineLayout layout,
            BedrockTorchPlacement placement,
            BlockPos slimePos,
            long plannedAtTick
    ) {
    }
}
