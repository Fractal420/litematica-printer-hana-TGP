package me.aleksilassila.litematica.printer.mixin.printer.mc;

import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC >= 12103
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
//#endif

@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput {
    //#if MC >= 12103
    @Inject(method = "tick", at = @At("TAIL"))
    private void litematica_printer$blockMovementDuringVanillaRefill(CallbackInfo ci) {
        litematica_printer$zeroMovement();
    }
    //#else
    //$$ @Inject(method = "tick", at = @At("TAIL"))
    //$$ private void litematica_printer$blockMovementDuringVanillaRefill(boolean slowDown, float sneakingSpeedMultiplier, CallbackInfo ci) {
    //$$     litematica_printer$zeroMovement();
    //$$ }
    //#endif

    private void litematica_printer$zeroMovement() {
        if (!RuntimeAccess.get().manualVanillaRefill().shouldPause()) {
            return;
        }
        //#if MC >= 12103
        KeyboardInput self = (KeyboardInput) (Object) this;
        self.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) (Object) this).litematica_printer$setMoveVector(Vec2.ZERO);
        //#else
        //$$ KeyboardInput self = (KeyboardInput) (Object) this;
        //$$ self.forwardImpulse = 0.0F;
        //$$ self.leftImpulse = 0.0F;
        //$$ self.jumping = false;
        //#endif
    }
}
