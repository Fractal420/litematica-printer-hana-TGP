package me.aleksilassila.litematica.printer.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudVisibilityPolicyTest {
    @Test
    void hidesEveryPrinterHudWhenTheMasterSwitchIsOff() {
        assertFalse(HudVisibilityPolicy.shouldRender(false, true, true));
        assertFalse(HudVisibilityPolicy.shouldRender(false, true, false));
        assertFalse(HudVisibilityPolicy.shouldRender(false, false, true));
    }

    @Test
    void requiresAtLeastOneHudToggleWhenTheMasterSwitchIsOn() {
        assertTrue(HudVisibilityPolicy.shouldRender(true, true, false));
        assertTrue(HudVisibilityPolicy.shouldRender(true, false, true));
        assertFalse(HudVisibilityPolicy.shouldRender(true, false, false));
    }
}
