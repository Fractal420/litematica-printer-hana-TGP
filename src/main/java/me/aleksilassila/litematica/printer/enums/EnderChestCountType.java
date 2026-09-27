package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;

public enum EnderChestCountType implements ConfigOptionListEntry<EnderChestCountType> {
    ONE("enderChestCount.one"),
    TWO("enderChestCount.two");

    private final I18n i18n;

    EnderChestCountType(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }

    public int count() {
        return this == TWO ? 2 : 1;
    }
}
