package me.aleksilassila.litematica.printer.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HudStatsManagerTest {
    @Test
    void usesTenTicksWhenRttIsUnavailableOrBelowTheBaseWindow() {
        assertEquals(10, HudStatsManager.confirmationTimeoutTicksFor(0));
        assertEquals(10, HudStatsManager.confirmationTimeoutTicksFor(8));
    }

    @Test
    void letsRttExtendTheConfirmationWindowForHighLatency() {
        assertEquals(12, HudStatsManager.confirmationTimeoutTicksFor(10));
        assertEquals(22, HudStatsManager.confirmationTimeoutTicksFor(20));
        assertEquals(42, HudStatsManager.confirmationTimeoutTicksFor(40));
    }
}
