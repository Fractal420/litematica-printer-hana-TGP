package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

final class BedrockScanActivityPolicy {
    private long retryWakeUntilTick = Long.MIN_VALUE;

    void reset() {
        this.retryWakeUntilTick = Long.MIN_VALUE;
    }

    void recordRetry(long currentTick, int cooldownTicks) {
        if (cooldownTicks <= 0) {
            return;
        }
        this.retryWakeUntilTick = Math.max(this.retryWakeUntilTick, currentTick + cooldownTicks);
    }

    boolean hasPendingWork(long currentTick, int activeTargetCount) {
        return activeTargetCount > 0 || currentTick < this.retryWakeUntilTick;
    }

    int getPendingWorkCount(long currentTick, int activeTargetCount) {
        if (activeTargetCount > 0) {
            return activeTargetCount;
        }
        return currentTick < this.retryWakeUntilTick ? 1 : 0;
    }
}
