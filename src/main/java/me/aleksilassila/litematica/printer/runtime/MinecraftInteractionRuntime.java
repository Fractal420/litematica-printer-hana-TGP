package me.aleksilassila.litematica.printer.runtime;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.utils.minecraft.NetworkUtils;
import net.minecraft.client.Minecraft;

final class MinecraftInteractionRuntime implements RuntimeComponent {
    private final Minecraft client;

    MinecraftInteractionRuntime(Minecraft client) {
        this.client = client;
    }

    @Override
    public void onEpochChanged(RuntimeEvent.EpochChanged event) {
        if (this.client.gameMode instanceof MultiPlayerGameModeExtension extension) {
            extension.litematica_printer$resetRuntime();
        }
        NetworkUtils.clearScopedLookOverride();
    }
}
