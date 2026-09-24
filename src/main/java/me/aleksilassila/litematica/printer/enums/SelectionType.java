package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;

public enum SelectionType implements ConfigOptionListEntry<SelectionType> {
    LITEMATICA_SELECTION("selectionType.litematica.selection"),
    LITEMATICA_RENDER_LAYER("selectionType.litematica.renderLayer"),
    LITEMATICA_SELECTION_BELOW_PLAYER_LAYER("selectionType.litematica.selection.belowPlayer.renderLayer"),
    LITEMATICA_SELECTION_BELOW_PLAYER("selectionType.litematica.selection.belowPlayer"),
    LITEMATICA_SELECTION_ABOVE_PLAYER_LAYER("selectionType.litematica.selection.abovePlayer.renderLayer"),
    LITEMATICA_SELECTION_ABOVE_PLAYER("selectionType.litematica.selection.abovePlayer");

    private final I18n i18n;

    SelectionType(String translateKey) {
        this.i18n = I18n.of(translateKey);
    }

    @Override
    public I18n getI18n() {
        return i18n;
    }

    public boolean isBelowPlayer() {
        return this == LITEMATICA_SELECTION_BELOW_PLAYER
                || this == LITEMATICA_SELECTION_BELOW_PLAYER_LAYER;
    }

    public boolean isAbovePlayer() {
        return this == LITEMATICA_SELECTION_ABOVE_PLAYER
                || this == LITEMATICA_SELECTION_ABOVE_PLAYER_LAYER;
    }

    public boolean requiresRenderLayer() {
        return this == LITEMATICA_RENDER_LAYER
                || this == LITEMATICA_SELECTION_BELOW_PLAYER_LAYER
                || this == LITEMATICA_SELECTION_ABOVE_PLAYER_LAYER;
    }
}
