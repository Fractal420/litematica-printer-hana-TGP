package me.aleksilassila.litematica.printer.handler.handlers;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;

final class MineCandidateQueue {
    private final LinkedHashMap<BlockPos, MineBreakExecutor.Target> entries = new LinkedHashMap<>();

    void add(MineBreakExecutor.Target target) {
        if (target != null && target.pos() != null) {
            this.entries.put(target.pos().immutable(), target);
        }
    }

    boolean isEmpty() {
        return this.entries.isEmpty();
    }

    int size() {
        return this.entries.size();
    }

    List<MineBreakExecutor.Target> ordered(Comparator<MineBreakExecutor.Target> comparator) {
        List<MineBreakExecutor.Target> result = new ArrayList<>(this.entries.values());
        result.sort(comparator);
        return result;
    }

    List<MineBreakExecutor.Target> snapshot() {
        return new ArrayList<>(this.entries.values());
    }

    void remove(BlockPos pos) {
        if (pos != null) {
            this.entries.remove(pos);
        }
    }

    void removeIf(Predicate<MineBreakExecutor.Target> predicate) {
        this.entries.entrySet().removeIf(entry -> predicate.test(entry.getValue()));
    }

    void clear() {
        this.entries.clear();
    }
}
