package me.aleksilassila.litematica.printer.handler.scan;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

interface WorldObservationPort {
    boolean hasChunk(int chunkX, int chunkZ);

    default boolean hasCandidatesInChunk(
            ScanIntent intent,
            int chunkX,
            int chunkZ,
            boolean breakExtraBlocks
    ) {
        return this.hasChunk(chunkX, chunkZ);
    }

    BlockState worldState(BlockPos pos);

    @Nullable BlockState schematicState(BlockPos pos);

    boolean hasFillSupport(BlockPos pos);

    default byte classify(ScanIntent intent, BlockPos pos, boolean breakExtraBlocks) {
        BlockState schematicState = intent == ScanIntent.PRINT ? this.schematicState(pos) : null;

        if (intent == ScanIntent.PRINT && !breakExtraBlocks
                && (schematicState == null || schematicState.isAir())) {
            return 0;
        }
        BlockState state = this.worldState(pos);
        boolean fillSupport = intent == ScanIntent.FILL && this.hasFillSupport(pos);
        return ScanClassifier.flags(intent, state, schematicState, fillSupport, breakExtraBlocks);
    }
}
