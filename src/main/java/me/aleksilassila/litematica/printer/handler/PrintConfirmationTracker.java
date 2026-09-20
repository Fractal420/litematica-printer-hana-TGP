package me.aleksilassila.litematica.printer.handler;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PrintConfirmationTracker {
    private final Map<BlockPos, PendingBlockState> pendingStates = new LinkedHashMap<>();

    void track(BlockPos pos, BlockState expectedState, long sentTick, int timeoutTicks) {
        this.pendingStates.put(
                pos.immutable(),
                new PendingBlockState(expectedState, sentTick + timeoutTicks)
        );
    }

    boolean isPending(BlockPos pos) {
        return this.pendingStates.containsKey(pos);
    }

    Resolution resolve(BlockPos pos, BlockState authoritativeState) {
        PendingBlockState pending = this.pendingStates.remove(pos);
        if (pending == null) {
            return null;
        }
        return authoritativeState.equals(pending.expectedState())
                ? Resolution.MATCHED
                : Resolution.MISMATCHED;
    }

    List<BlockPos> expire(long currentTick, int maxChecks) {
        List<BlockPos> expired = new ArrayList<>();
        for (int checked = 0; checked < maxChecks && !this.pendingStates.isEmpty(); checked++) {
            Iterator<Map.Entry<BlockPos, PendingBlockState>> iterator =
                    this.pendingStates.entrySet().iterator();
            Map.Entry<BlockPos, PendingBlockState> entry = iterator.next();
            iterator.remove();
            if (currentTick > entry.getValue().expireTick()) {
                expired.add(entry.getKey());
            } else {
                this.pendingStates.put(entry.getKey(), entry.getValue());
            }
        }
        return expired;
    }

    void clear() {
        this.pendingStates.clear();
    }

    enum Resolution {
        MATCHED,
        MISMATCHED
    }

    private record PendingBlockState(BlockState expectedState, long expireTick) {
    }
}
