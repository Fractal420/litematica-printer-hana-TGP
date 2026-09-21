package me.aleksilassila.litematica.printer.integration.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickBlockRequestPolicyTest {
    @Test
    void activeProviderConsumesTheInput() {
        assertTrue(PickBlockRequestPolicy.shouldConsume(
                MaterialReservation.State.PENDING,
                false
        ));
        assertTrue(PickBlockRequestPolicy.shouldConsume(
                MaterialReservation.State.AVAILABLE,
                false
        ));
    }

    @Test
    void takeItOutNativeHookIsSuppressedAfterUnifiedChainMisses() {
        assertTrue(PickBlockRequestPolicy.shouldConsume(
                MaterialReservation.State.UNAVAILABLE,
                true
        ));
        assertFalse(PickBlockRequestPolicy.shouldConsume(
                MaterialReservation.State.UNAVAILABLE,
                false
        ));
    }
}
