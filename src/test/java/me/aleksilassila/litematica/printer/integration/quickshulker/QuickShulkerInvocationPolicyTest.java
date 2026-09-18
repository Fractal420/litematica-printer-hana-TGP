package me.aleksilassila.litematica.printer.integration.quickshulker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickShulkerInvocationPolicyTest {
    @Test
    void easyPlaceStartsTheQuickShulkerRequestImmediately() {
        assertTrue(QuickShulkerInvocationPolicy.startsImmediately(true));
    }

    @Test
    void physicalPickBlockKeepsTheDuplicateInputDebounce() {
        assertFalse(QuickShulkerInvocationPolicy.startsImmediately(false));
    }

    @Test
    void anArmedContainerScreenTokenSuppressesExactlyTheContainerScreen() {
        assertTrue(QuickShulkerInvocationPolicy.shouldSuppressScreen(1, true));
        assertFalse(QuickShulkerInvocationPolicy.shouldSuppressScreen(0, true));
        assertFalse(QuickShulkerInvocationPolicy.shouldSuppressScreen(1, false));
    }

    @Test
    void acceptedMaterialRequestBypassesLitematicaNativeShulkerSelection() {
        assertTrue(QuickShulkerInvocationPolicy.shouldBypassLitematicaPickBlock(true));
    }

    @Test
    void unavailableMaterialRequestLeavesLitematicaPickBlockUntouched() {
        assertFalse(QuickShulkerInvocationPolicy.shouldBypassLitematicaPickBlock(false));
    }
}
