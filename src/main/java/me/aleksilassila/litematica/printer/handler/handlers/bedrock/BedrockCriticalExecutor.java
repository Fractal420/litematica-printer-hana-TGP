package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import java.util.Set;

final class BedrockCriticalExecutor {
    private long currentTick = Long.MIN_VALUE;
    private int reservedPistons;
    private final BedrockPlacer placer;
    private final BedrockRunStats stats;

    BedrockCriticalExecutor(BedrockPlacer placer) {
        this(placer, null);
    }

    BedrockCriticalExecutor(BedrockPlacer placer, BedrockRunStats stats) {
        this.placer = placer;
        this.stats = stats;
    }

    BedrockPlacer placer() {
        return this.placer;
    }

    void reset() {
        currentTick = Long.MIN_VALUE;
        reservedPistons = 0;
    }

    void beginTick(long tick) {
        if (currentTick == tick) {
            return;
        }
        currentTick = tick;
        reservedPistons = 0;
    }

    boolean submit(
            ClientLevel level,
            BlockPos bedrockPos,
            BlockPos pistonPos,
            Direction executeFacing,
            Set<BlockPos> ownedTorchPositions
    ) {
        if (level == null || bedrockPos == null || pistonPos == null || executeFacing == null) {
            return false;
        }
        if (!BedrockTargetBlocks.isTargetBlock(level.getBlockState(bedrockPos))) {
            return false;
        }

        var pistonState = level.getBlockState(pistonPos);
        if (!pistonState.is(Blocks.PISTON)
                || !pistonState.getValue(PistonBaseBlock.EXTENDED)
                || !BedrockInventory.hasAtLeast(Items.PISTON, reservedPistons + 1)) {
            return false;
        }
        if (!BedrockBreaker.prepareCriticalTool(pistonPos, pistonState)) {
            return false;
        }

        reservedPistons++;

        if (ownedTorchPositions != null) {
            for (BlockPos torchPos : ownedTorchPositions) {
                BedrockBreaker.sendCriticalBreakPackets(torchPos, Direction.DOWN);
            }
        }

        BedrockBreaker.sendCriticalBreakPackets(pistonPos, executeFacing);
        boolean accepted = this.placer.placePiston(pistonPos, executeFacing);
        if (accepted) {
            this.placer.schedulePistonRetry(bedrockPos, pistonPos, executeFacing);
        } else {
            reservedPistons--;
            if (this.stats != null) this.stats.lastReason = "piston_place_failed";
        }
        return accepted;
    }
}
