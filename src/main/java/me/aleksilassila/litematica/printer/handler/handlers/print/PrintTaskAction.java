package me.aleksilassila.litematica.printer.handler.handlers.print;

import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.state.BlockState;

public interface PrintTaskAction {

    default BlockState expectedBlockState(SchematicBlockContext context, Action action) {
        return context.requiredState;
    }

    default void onQueued(SchematicBlockContext context, Action action) {
    }

    void onSuccess(SchematicBlockContext context, Action action);

    void onFailure(SchematicBlockContext context, Action action);

    default void onCancelled(SchematicBlockContext context, Action action) {
    }

    default boolean stopIterationAfterAction() {
        return true;
    }
}
