package me.aleksilassila.litematica.printer.handler.handlers.print;

public enum WaterTaskStage {
    RESERVED,
    REMOVE_EXISTING,
    WAIT_REMOVE_CONFIRM,
    PLACE_ICE,
    WAIT_ICE_CONFIRM,
    BREAK_ICE,
    WAIT_WATER_CONFIRM,
    PLACE_FINAL_BLOCK,
    WAIT_FINAL_CONFIRM,
    RETRY_WAIT,
    COMPLETE;

    boolean waitsForWorldUpdate() {
        return this == WAIT_REMOVE_CONFIRM
                || this == WAIT_ICE_CONFIRM
                || this == WAIT_WATER_CONFIRM
                || this == WAIT_FINAL_CONFIRM;
    }
}
