package me.aleksilassila.litematica.printer.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import me.aleksilassila.litematica.printer.I18n;

import java.util.Arrays;

public interface ConfigOptionListEntry<T extends Enum<T> & ConfigOptionListEntry<T>> extends IConfigOptionListEntry {
    I18n getI18n();

    @Override
    default String getStringValue() {
        return this.getI18n().getSimpleKey();
    }

    @Override
    default String getDisplayName() {
        return this.getI18n().getConfigName().getString();
    }

    @Override
    @SuppressWarnings({"unchecked"})
    default IConfigOptionListEntry cycle(boolean forward) {
        if (!(this instanceof Enum<?> enumInstance)) {
            throw new IllegalStateException("ConfigOptionListEntry 仅支持枚举实现！");
        }
        Enum<?>[] allValues = enumInstance.getDeclaringClass().getEnumConstants();
        int currentOrdinal = enumInstance.ordinal();
        int newOrdinal = forward
                ? (currentOrdinal + 1) % allValues.length
                : (currentOrdinal - 1 + allValues.length) % allValues.length;
        return (T) allValues[newOrdinal];
    }

    @Override
    @SuppressWarnings({"unchecked"})
    default T fromString(String name) {
        if (!(this instanceof Enum<?> enumInstance)) {
            throw new IllegalStateException("ConfigOptionListEntry 仅支持枚举实现！");
        }
        Class<T> enumClass = (Class<T>) enumInstance.getDeclaringClass();
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(enumEntry -> enumEntry.getStringValue().equalsIgnoreCase(name))
                .findFirst()

                .orElse(enumClass.getEnumConstants()[0]);
    }

    static <T extends Enum<T> & ConfigOptionListEntry<T>> T fromStringStatic(Class<T> enumClass, String name) {
        return Arrays.stream(enumClass.getEnumConstants())
                .filter(enumEntry -> enumEntry.getStringValue().equalsIgnoreCase(name))
                .findFirst()
                .orElse(enumClass.getEnumConstants()[0]);
    }
}
