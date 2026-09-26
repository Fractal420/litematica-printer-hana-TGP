package me.aleksilassila.litematica.printer.render;

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
