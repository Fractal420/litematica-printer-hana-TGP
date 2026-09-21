package me.aleksilassila.litematica.printer.mixin;

import me.aleksilassila.litematica.printer.utils.mods.QuickShulkerBridge;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.phys.BlockHitResult;

@Environment(EnvType.CLIENT)
// Priority rationale: Printer must serialize a missing-item pick request before Take It Out's
// default-priority HEAD hook can independently dispatch the same request a second time.
@Mixin(value = Minecraft.class, priority = 1100)
public abstract class MixinMinecraftPickBlock {

    //#if MC > 260100
    @Inject(method = "pickBlockOrEntity", at = @At("HEAD"), cancellable = true)
    //#else
    //$$ @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
    //#endif
    private void litematica_printer$pickRealBlock(CallbackInfo ci) {
        Minecraft client = (Minecraft) (Object) this;
        if (client.level == null
                || client.player == null
                || !(client.hitResult instanceof BlockHitResult hitResult)) {
            return;
        }
        Item item = client.level.getBlockState(hitResult.getBlockPos()).getBlock().asItem();
        if (item != Items.AIR && QuickShulkerBridge.handlePickBlock(client.player, item)) {
            ci.cancel();
        }
    }

}
