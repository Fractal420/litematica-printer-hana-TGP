package me.aleksilassila.litematica.printer.integration.quickshulker;

public final class QuickShulkerInvocationPolicy {
    private QuickShulkerInvocationPolicy() {
    }

    public static boolean allowsPickBlock(boolean instabuild, boolean spectator) {
        return !instabuild && !spectator;
    }

    public static boolean shouldSuppressScreen(int pendingTokens, boolean containerScreen) {
        return pendingTokens > 0 && containerScreen;
    }

}
