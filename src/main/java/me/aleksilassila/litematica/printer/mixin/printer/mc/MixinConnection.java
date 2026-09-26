package me.aleksilassila.litematica.printer.mixin.printer.mc;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.guide.guides.RailGuide;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(Connection.class)
public class MixinConnection {
    @Inject(method = "genericsFtw", at = @At("HEAD"), require = 0)
    private static void hookGenericsFtw(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
        if (ConfigUtils.isEnable()) {
            RuntimeAccess.get().modules().recordInboundPacket();
        }
    }

    @Inject(method = "disconnect*", at = {@At("HEAD")})
    public void disconnect(Component ignored, CallbackInfo ci) {
        RuntimeAccess.get().reset("disconnect");
        RailGuide.clearRepairState();
        if (Configs.Core.AUTO_DISABLE_PRINTER.getBooleanValue() && Configs.Core.WORK_SWITCH.getBooleanValue()) {
            Configs.Core.WORK_SWITCH.setBooleanValue(false);
        }
    }
}
