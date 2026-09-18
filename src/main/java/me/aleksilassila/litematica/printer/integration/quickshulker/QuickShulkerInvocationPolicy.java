package me.aleksilassila.litematica.printer.integration.quickshulker;

/** Timing rules shared by the Easy Place hook and the hidden container screen. */
public final class QuickShulkerInvocationPolicy {
    private QuickShulkerInvocationPolicy() {
    }

    public static boolean startsImmediately(boolean easyPlace) {
        return easyPlace;
    }

    public static boolean shouldSuppressScreen(int pendingTokens, boolean containerScreen) {
        return pendingTokens > 0 && containerScreen;
    }

    public static boolean shouldBypassLitematicaPickBlock(boolean materialRequestAccepted) {
        return materialRequestAccepted;
    }
}
