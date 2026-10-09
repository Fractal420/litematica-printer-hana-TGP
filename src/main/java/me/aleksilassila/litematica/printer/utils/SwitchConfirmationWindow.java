package me.aleksilassila.litematica.printer.utils;

final class SwitchConfirmationWindow {
    private final int maxSettleTicks;
    private int minSettleTicks;
    private long startedTick = Long.MIN_VALUE;

    SwitchConfirmationWindow(int maxSettleTicks) {
        this.maxSettleTicks = maxSettleTicks;
        this.minSettleTicks = Math.max(2, maxSettleTicks / 6);
    }

    void begin(long tick) {
        begin(tick, this.minSettleTicks);
    }

    void begin(long tick, int minSettleTicks) {
        this.startedTick = tick;
        this.minSettleTicks = Math.max(2, Math.min(this.maxSettleTicks, minSettleTicks));
    }

    boolean isWaiting(long tick, boolean handMatches) {
        if (!this.isActive()) {
            return false;
        }
        long age = tick - this.startedTick;
        if (age < this.minSettleTicks) {
            return true;
        }
        if (handMatches) {
            this.clear();
            return false;
        }
        if (age > this.maxSettleTicks) {
            this.clear();
            return false;
        }
        return true;
    }

    void clear() {
        this.startedTick = Long.MIN_VALUE;
    }

    boolean isActive() {
        return this.startedTick != Long.MIN_VALUE;
    }

    int minSettleTicks() {
        return this.minSettleTicks;
    }
}
