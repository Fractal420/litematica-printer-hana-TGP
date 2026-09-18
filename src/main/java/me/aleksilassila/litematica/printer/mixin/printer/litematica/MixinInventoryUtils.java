package me.aleksilassila.litematica.printer.mixin.printer.litematica;


import fi.dy.masa.litematica.util.InventoryUtils;
import me.aleksilassila.litematica.printer.integration.quickshulker.QuickShulkerInvocationPolicy;
import me.aleksilassila.litematica.printer.utils.mods.ChestTrackerBridge;
import me.aleksilassila.litematica.printer.utils.mods.QuickShulkerBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryUtils.class)
public class MixinInventoryUtils {
    @Inject(at = @At("HEAD"), method = "schematicWorldPickBlock", cancellable = true)
    private static void schematicWorldPickBlock(ItemStack stack, BlockPos pos, Level schematicWorld, Minecraft mc, CallbackInfo ci) {
        if (mc.player == null || mc.player.getAbilities().instabuild || mc.player.isSpectator()) {
            return;
        }
        if (ChestTrackerBridge.handlePickBlock(mc.player, stack.getItem())) {
            ci.cancel();
            return;
        }
        boolean materialRequestAccepted = QuickShulkerBridge.handleEasyPlacePickBlock(mc.player, stack);
        if (QuickShulkerInvocationPolicy.shouldBypassLitematicaPickBlock(materialRequestAccepted)) {
            // Litematica's fallback selects the containing shulker box before Printer can extract
            // the requested stack. Cancelling here prevents the box name and hotbar move; Easy
            // Place retries normally after the server has delivered the requested material.
            ci.cancel();
        }
    }

}
