package me.aleksilassila.litematica.printer.utils;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;

public final class CooldownUtils implements RuntimeComponent {
    private final Map<Info, Integer> cooldownMap = new HashMap<>();

    public CooldownUtils() {
    }

    public void tick() {
        if (!ConfigUtils.isEnable()) {
            if (!cooldownMap.isEmpty()) {
                cooldownMap.clear();
            }
            return;
        }
        if (cooldownMap.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<Info, Integer>> iterator = cooldownMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Info, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iterator.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public void setCooldown(ClientLevel level, String type, BlockPos pos, int cooldownTicks) {
        if (cooldownTicks <= 0) return;
        Identifier dimension = level.dimension().identifier();
        Info key = new Info(dimension, type, pos);
        cooldownMap.put(key, cooldownTicks);
    }

    public boolean isOnCooldown(ClientLevel level, String type, BlockPos pos) {
        Identifier dimension = level.dimension().identifier();
        Info key = new Info(dimension, type, pos);
        return cooldownMap.containsKey(key);
    }

    public void removeCooldown(ClientLevel level, String type, BlockPos pos) {
        Identifier dimension = level.dimension().identifier();
        Info key = new Info(dimension, type, pos);
        cooldownMap.remove(key);
    }

    public int getRemainingCooldown(ClientLevel level, String type, BlockPos pos) {
        Identifier dimension = level.dimension().identifier();
        Info key = new Info(dimension, type, pos);
        return cooldownMap.getOrDefault(key, 0);
    }

    public void clearDimensionCooldowns(ClientLevel level) {
        Identifier dimension = level.dimension().identifier();
        cooldownMap.keySet().removeIf(info -> info.dimension.equals(dimension));
    }

    public void clearTypeCooldowns(ClientLevel level, String type) {
        Identifier dimension = level.dimension().identifier();
        cooldownMap.keySet().removeIf(info -> info.dimension.equals(dimension) && info.type.equals(type));
    }

    public void clearAllCooldowns() {
        cooldownMap.clear();
    }

    @Override public void onEpochChanged(RuntimeEvent.EpochChanged event) { this.clearAllCooldowns(); }

    @SuppressWarnings("ClassCanBeRecord")
    private static final class Info {
        private final Identifier dimension;
        private final String type;
        private final BlockPos pos;

        private Info(Identifier dimension, String type, BlockPos pos) {
            this.dimension = Objects.requireNonNull(dimension, "Dimension Identifier cannot be null!");
            this.type = Objects.requireNonNull(type, "Cool down type cannot be null!");
            this.pos = Objects.requireNonNull(pos, "BlockPos cannot be null!");
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, type, pos);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Info info = (Info) obj;
            return Objects.equals(dimension, info.dimension)
                    && Objects.equals(type, info.type)
                    && Objects.equals(pos, info.pos);
        }
    }
}
