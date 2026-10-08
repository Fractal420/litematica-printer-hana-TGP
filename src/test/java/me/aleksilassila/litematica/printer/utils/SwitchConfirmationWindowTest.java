package me.aleksilassila.litematica.printer.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwitchConfirmationWindowTest {
    @Test
    void clientPredictionCannotReleaseSwitchInTheSameTick() {
        SwitchConfirmationWindow window = new SwitchConfirmationWindow(4);
        window.begin(100L);

        assertTrue(window.isWaiting(100L, true));
        assertFalse(window.isWaiting(101L, true));
    }

    @Test
    void unmatchedSwitchWaitsButEventuallyAllowsSelectionRetry() {
        SwitchConfirmationWindow window = new SwitchConfirmationWindow(4);
        window.begin(100L);

        assertTrue(window.isWaiting(101L, false));
        assertTrue(window.isWaiting(104L, false));
        assertFalse(window.isWaiting(105L, false));
    }
}
