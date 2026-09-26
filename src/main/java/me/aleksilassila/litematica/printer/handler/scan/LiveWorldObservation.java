package me.aleksilassila.litematica.printer.handler.scan;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.utils.minecraft.BlockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

final class LiveWorldObservation implements WorldObservationPort {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final ClientLevel level;
    @Nullable
    private final WorldSchematic schematic;
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();

    LiveWorldObservation(ClientLevel level, @Nullable WorldSchematic schematic) {
        this.level = level;
        this.schematic = schematic;
    }

    @Override
    public boolean hasChunk(int chunkX, int chunkZ) {
        return this.level.hasChunk(chunkX, chunkZ);
    }

    @Override
    public boolean hasCandidatesInChunk(
            ScanIntent intent,
            int chunkX,
            int chunkZ,
            boolean breakExtraBlocks
    ) {
        if (!this.level.hasChunk(chunkX, chunkZ)) return false;
        return intent != ScanIntent.PRINT
                || breakExtraBlocks
                || this.schematic == null
                || !this.schematic.getChunk(chunkX, chunkZ).isEmpty();
    }

    @Override
    public BlockState worldState(BlockPos pos) {
        return this.level.getBlockState(pos);
    }

    @Override
    public @Nullable BlockState schematicState(BlockPos pos) {
        return this.schematic == null ? null : this.schematic.getBlockState(pos);
    }

    @Override
    public boolean hasFillSupport(BlockPos pos) {
        for (Direction direction : DIRECTIONS) {
            this.neighbor.set(
                    pos.getX() + direction.getStepX(),
                    pos.getY() + direction.getStepY(),
                    pos.getZ() + direction.getStepZ()
            );
            BlockState state = this.level.getBlockState(this.neighbor);
            if (!state.isAir()
                    && !(state.getBlock() instanceof LiquidBlock)
                    && !BlockUtils.isReplaceable(state)) {
                return true;
            }
        }
        return false;
    }
}
