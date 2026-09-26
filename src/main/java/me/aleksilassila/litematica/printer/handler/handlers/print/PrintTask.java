package me.aleksilassila.litematica.printer.handler.handlers.print;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface PrintTask {
    BlockPos pos();

    default boolean owns(@Nullable BlockPos pos) {
        return pos != null && pos.equals(this.pos());
    }

    boolean shouldKeep(ClientLevel level, WorldSchematic schematic);

    default boolean isWaitingForWorldUpdate(ClientLevel level, WorldSchematic schematic) {
        return false;
    }

    default long nextCheckTick() {
        return Long.MIN_VALUE;
    }

    PrintTaskBuildResult buildAction(SchematicBlockContext context);

    @Nullable
    PrintTaskAction createActionHandle(SchematicBlockContext context, Action action);
}
