package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

final class BedrockThroughputScheduler {
    private long tick;
    private int configuredThroughput = -1;
    private int configuredInterval = -1;

    void reset() {
        this.tick = 0L;
        this.configuredThroughput = -1;
        this.configuredInterval = -1;
    }

    Allocation allocate(int requestedThroughput, int requestedInterval) {
        int throughput = Math.max(1, requestedThroughput);
        int interval = Math.max(1, requestedInterval);
        if (throughput != this.configuredThroughput || interval != this.configuredInterval) {
            this.configuredThroughput = throughput;
            this.configuredInterval = interval;
            this.tick = 0L;
        }
        boolean window = this.tick++ % interval == 0L;
        int total = window ? throughput : 0;
        int critical = (total + 1) / 2;
        return new Allocation(total, critical, total - critical, interval);
    }

    void consume(Allocation allocation, int unusedActions) {
        if (allocation == null) {
            return;
        }

    }

    record Allocation(int total, int critical, int preparation, int interval) {
    }
}
