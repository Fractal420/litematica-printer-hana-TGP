package me.aleksilassila.litematica.printer.core.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class RuntimeEventBus {
    private final List<Consumer<RuntimeEvent>> listeners = new CopyOnWriteArrayList<>();

    public AutoCloseable subscribe(Consumer<RuntimeEvent> listener) {
        this.listeners.add(listener);
        return () -> this.listeners.remove(listener);
    }

    public void publish(RuntimeEvent event) {
        for (Consumer<RuntimeEvent> listener : this.listeners) {
            listener.accept(event);
        }
    }

    public void clear() {
        this.listeners.clear();
    }
}
