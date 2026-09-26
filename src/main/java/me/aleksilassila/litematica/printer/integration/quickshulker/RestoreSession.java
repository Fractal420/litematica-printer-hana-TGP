package me.aleksilassila.litematica.printer.integration.quickshulker;

final class RestoreSession<T> {
    private T pending;
    private boolean waitingForContainer;
    private int timeoutTicks;
    private boolean pressureRecoveryActive;
    private long lastActivityTick = Long.MIN_VALUE;

    void reset() {
        this.clearPending();
        this.pressureRecoveryActive = false;
        this.lastActivityTick = Long.MIN_VALUE;
    }

    boolean schedule(T value) {
        if (value == null || this.pending != null) {
            return false;
        }
        this.pending = value;
        return true;
    }

    T pending() {
        return this.pending;
    }

    boolean hasPending() {
        return this.pending != null;
    }

    boolean isWaitingForContainer() {
        return this.waitingForContainer && this.pending != null;
    }

    void beginContainerWait(int timeoutTicks) {
        if (this.pending == null) {
            return;
        }
        this.waitingForContainer = true;
        this.timeoutTicks = Math.max(0, timeoutTicks);
    }

    void stopContainerWait() {
        this.waitingForContainer = false;
        this.timeoutTicks = 0;
    }

    boolean tickContainerTimeout() {
        if (!this.waitingForContainer || this.timeoutTicks <= 0) {
            return false;
        }
        this.timeoutTicks--;
        return this.timeoutTicks == 0;
    }

    T clearPending() {
        T previous = this.pending;
        this.pending = null;
        this.stopContainerWait();
        return previous;
    }

    void markActivity(long currentTick) {
        this.lastActivityTick = currentTick;
    }

    void clearActivity() {
        this.lastActivityTick = Long.MIN_VALUE;
    }

    void normalizeActivity(long currentTick) {
        if (this.lastActivityTick == Long.MIN_VALUE || currentTick < this.lastActivityTick) {
            this.lastActivityTick = currentTick;
        }
    }

    long lastActivityTick() {
        return this.lastActivityTick;
    }

    void updatePressureRecovery(int freeSlots, int triggerFreeSlots, int targetFreeSlots) {
        if (freeSlots <= triggerFreeSlots) {
            this.pressureRecoveryActive = true;
        } else if (freeSlots >= targetFreeSlots) {
            this.pressureRecoveryActive = false;
        }
    }

    boolean isPressureRecoveryActive() {
        return this.pressureRecoveryActive;
    }

    void clearPressureRecovery() {
        this.pressureRecoveryActive = false;
    }
}
