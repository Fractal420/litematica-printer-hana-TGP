package me.aleksilassila.litematica.printer.mixin;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftManualUse {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$blockUseDuringVanillaRefill(CallbackInfo ci) {
        if (RuntimeAccess.get().manualVanillaRefill().shouldPause()) {
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$blockAttackDuringVanillaRefill(CallbackInfoReturnable<Boolean> cir) {
        if (RuntimeAccess.get().manualVanillaRefill().shouldPause()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void litematica_printer$blockContinueAttackDuringVanillaRefill(boolean leftClick, CallbackInfo ci) {
        if (RuntimeAccess.get().manualVanillaRefill().shouldPause()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void preserveManualAnvilScreens(CallbackInfo ci) {
        if (!Configs.Core.WORK_SWITCH.getBooleanValue()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level != null
                && client.hitResult instanceof BlockHitResult blockHit
                && client.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof AnvilBlock) {
            RuntimeAccess.get().actionBroker().prioritizeManualAnvilScreen();
        }
    }
}
