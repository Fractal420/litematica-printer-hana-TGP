package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.config.Configs;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

final class ScanBudget {
    private final LongSupplier nanoClock;
    private final LongSupplier budgetNanosSupplier;
    private final Map<String, Long> consumedNanos = new HashMap<>();
    private long tickTime = Long.MIN_VALUE;

    ScanBudget() {
        this(System::nanoTime, ScanBudget::configuredBudgetNanos);
    }

    ScanBudget(LongSupplier nanoClock) {
        this(nanoClock, ScanBudget::configuredBudgetNanos);
    }

    ScanBudget(LongSupplier nanoClock, LongSupplier budgetNanosSupplier) {
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.budgetNanosSupplier = Objects.requireNonNull(budgetNanosSupplier, "budgetNanosSupplier");
    }

    void reset() {
        this.tickTime = Long.MIN_VALUE;
        this.consumedNanos.clear();
    }

    void beginTick(long tickTime) {
        if (this.tickTime == tickTime) {
            return;
        }
        this.tickTime = tickTime;
        this.consumedNanos.clear();
    }

    boolean isExceeded(String ownerKey, long ownerStartNanos) {
        return this.consumedNanos.getOrDefault(ownerKey, 0L) + elapsedSince(ownerStartNanos)
                >= Math.max(1L, this.budgetNanosSupplier.getAsLong());
    }

    void record(String ownerKey, ScanMetricsAccumulator metrics, long ownerStartNanos) {
        long elapsed = elapsedSince(ownerStartNanos);
        metrics.scanNanos += elapsed;
        this.consumedNanos.merge(ownerKey, elapsed, Long::sum);
    }

    private long elapsedSince(long startNanos) {
        return Math.max(0L, this.nanoClock.getAsLong() - startNanos);
    }

    private static long configuredBudgetNanos() {
        return Math.max(1L, Configs.Core.SCAN_TIME_BUDGET_MS.getIntegerValue()) * 1_000_000L;
    }
}
