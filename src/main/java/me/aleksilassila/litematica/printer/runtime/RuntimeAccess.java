package me.aleksilassila.litematica.printer.runtime;

import java.util.Objects;

public final class RuntimeAccess {
    private static volatile PrinterRuntime current;

    private RuntimeAccess() {
    }

    public static void install(PrinterRuntime runtime) {
        current = Objects.requireNonNull(runtime, "runtime");
    }

    public static PrinterRuntime get() {
        PrinterRuntime runtime = current;
        if (runtime == null) {
            throw new IllegalStateException("PrinterRuntime has not been installed");
        }
        return runtime;
    }
}
