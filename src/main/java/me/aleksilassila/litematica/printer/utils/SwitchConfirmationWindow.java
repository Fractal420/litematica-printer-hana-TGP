package me.aleksilassila.litematica.printer.utils;

final class SwitchConfirmationWindow {
    private final int maxSettleTicks;
    private long startedTick = Long.MIN_VALUE;

    SwitchConfirmationWindow(int maxSettleTicks) {
        this.maxSettleTicks = maxSettleTicks;
    }

    void begin(long tick) {
        this.startedTick = tick;
    }

    boolean isWaiting(long tick, boolean handMatches) {
        if (!this.isActive()) return false;
        long age = tick - this.startedTick;
        if (age <= 0L) return true;
        if (handMatches || age > this.maxSettleTicks) {
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
}
