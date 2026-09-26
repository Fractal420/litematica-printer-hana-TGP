package me.aleksilassila.litematica.printer.core.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RuntimeScope implements AutoCloseable {
    private final List<RuntimeComponent> components = new CopyOnWriteArrayList<>();

    public AutoCloseable register(RuntimeComponent component) {
        if (!this.components.contains(component)) this.components.add(component);
        return () -> this.components.remove(component);
    }

    public void changeEpoch(RuntimeEvent.EpochChanged event) {
        for (RuntimeComponent component : this.components) component.onEpochChanged(event);
    }

    public int componentCount() {
        return this.components.size();
    }

    @Override
    public void close() {
        for (int index = this.components.size() - 1; index >= 0; index--) {
            this.components.get(index).close();
        }
        this.components.clear();
    }
}
