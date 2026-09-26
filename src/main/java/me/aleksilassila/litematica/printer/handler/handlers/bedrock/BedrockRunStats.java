package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

final class BedrockRunStats {
    int acceptedThisTick;
    int rejectedThisTick;
    int confirmedSuccesses;
    int submittedTargets;
    int failedTargets;
    int stuckTargets;
    String lastReason = "idle";

    void reset() {
        this.acceptedThisTick = 0;
        this.rejectedThisTick = 0;
        this.confirmedSuccesses = 0;
        this.submittedTargets = 0;
        this.failedTargets = 0;
        this.stuckTargets = 0;
        this.lastReason = "idle";
    }

    void beginTick() {
        this.acceptedThisTick = 0;
        this.rejectedThisTick = 0;
    }

    double successRate() {
        int failures = this.failedTargets + this.stuckTargets;
        int resolved = this.confirmedSuccesses + failures;
        return resolved > 0 ? (double) this.confirmedSuccesses / (double) resolved : 0.0D;
    }
}
