package me.aleksilassila.litematica.printer.printer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RttReplayControllerTest {
    @Test
    void convertsRoundTripMillisToTicksWithSafetyFactor() {
        assertEquals(0, RttReplayController.intervalTicksFor(0.0D, 100));
        assertEquals(1, RttReplayController.intervalTicksFor(50.0D, 100));
        assertEquals(2, RttReplayController.intervalTicksFor(100.0D, 100));
        assertEquals(4, RttReplayController.intervalTicksFor(200.0D, 100));
        assertEquals(10, RttReplayController.intervalTicksFor(500.0D, 100));
        assertEquals(3, RttReplayController.intervalTicksFor(100.0D, 125));
    }

    @Test
    void capsExtremeLatencyAndRejectsNegativeSafety() {
        assertEquals(40, RttReplayController.intervalTicksFor(10_000.0D, 300));
        assertEquals(0, RttReplayController.intervalTicksFor(250.0D, -1));
    }

    @Test
    void derivesABoundedServerWaitFromRtt() {
        assertEquals(10, RttReplayController.waitTimeoutTicksFor(0, 8, 2, 40));
        assertEquals(14, RttReplayController.waitTimeoutTicksFor(4, 8, 2, 40));
        assertEquals(40, RttReplayController.waitTimeoutTicksFor(100, 8, 2, 40));
        assertEquals(10, RttReplayController.waitTimeoutTicksFor(-1, 8, 2, 40));
    }
}
