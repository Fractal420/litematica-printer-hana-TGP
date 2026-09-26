package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

final class BedrockTargetState {
    private int tickTimes;
    private boolean hasTried;
    private int stuckTicks;
    private int executeTick = -1;
    private int initializeTick = -1;
    private boolean throughputConsumed;
    private BedrockTarget.Status status = BedrockTarget.Status.UNINITIALIZED;

    void beginTick(boolean allowExecute) {
        this.throughputConsumed = false;
        if (this.status != BedrockTarget.Status.UNINITIALIZED
                && this.status != BedrockTarget.Status.EXTENDED) {
            this.tickTimes++;
        } else if (this.status == BedrockTarget.Status.EXTENDED && allowExecute) {
            this.tickTimes++;
        }
    }

    void advanceStatusOnly() {
        this.tickTimes++;
    }

    void resetAttempt() {
        this.tickTimes = 0;
        this.hasTried = false;
        this.stuckTicks = 0;
        this.executeTick = -1;
        this.initializeTick = -1;
    }

    int tickTimes() {
        return this.tickTimes;
    }

    boolean hasTried() {
        return this.hasTried;
    }

    void setHasTried(boolean value) {
        this.hasTried = value;
    }

    int stuckTicks() {
        return this.stuckTicks;
    }

    void incrementStuckTicks() {
        this.stuckTicks++;
    }

    int executeTick() {
        return this.executeTick;
    }

    void setExecuteTick(int value) {
        this.executeTick = value;
    }

    int initializeTick() {
        return this.initializeTick;
    }

    void setInitializeTick(int value) {
        this.initializeTick = value;
    }

    BedrockTarget.Status status() {
        return this.status;
    }

    void setStatus(BedrockTarget.Status status) {
        this.status = status;
    }

    boolean consumedThroughput() {
        return this.throughputConsumed;
    }

    void markThroughputAction() {
        this.throughputConsumed = true;
    }
}
