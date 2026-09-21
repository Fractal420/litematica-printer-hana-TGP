package me.aleksilassila.litematica.printer.render;

/** Determines whether Printer may contribute any HUD elements for the current frame. */
public final class HudVisibilityPolicy {
    private HudVisibilityPolicy() {
    }

    public static boolean shouldRender(
            boolean workEnabled,
            boolean renderHud,
            boolean renderMissingMaterials
    ) {
        return workEnabled && (renderHud || renderMissingMaterials);
    }
}
