package me.aleksilassila.litematica.printer.handler;

import me.aleksilassila.litematica.printer.handler.handlers.PrintHandler;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;

@Deprecated(forRemoval = false)
public final class ClientPlayerTickManager {
    public static final PrintHandler PRINT = RuntimeAccess.get().modules().print();

    private ClientPlayerTickManager() {
    }
}
