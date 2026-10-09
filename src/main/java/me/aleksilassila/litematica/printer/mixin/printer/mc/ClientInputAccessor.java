package me.aleksilassila.litematica.printer.mixin.printer.mc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//#if MC >= 12105
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector")
    void litematica_printer$setMoveVector(Vec2 value);
}
//#elseif MC >= 12103
//$$ import net.minecraft.client.player.ClientInput;
//$$
//$$ @Mixin(ClientInput.class)
//$$ public interface ClientInputAccessor {
//$$ }
//#else
//$$ @Mixin(targets = "net.minecraft.client.player.Input")
//$$ public interface ClientInputAccessor {
//$$ }
//#endif
