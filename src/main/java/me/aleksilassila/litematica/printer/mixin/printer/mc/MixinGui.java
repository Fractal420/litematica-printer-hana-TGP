package me.aleksilassila.litematica.printer.mixin.printer.mc;

import me.aleksilassila.litematica.printer.render.Render2D;
import me.aleksilassila.litematica.printer.render.HudVisibilityPolicy;
import me.aleksilassila.litematica.printer.utils.render.Render2DUtils;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
//#if MC >= 260200
//$$ import net.minecraft.client.gui.Hud;
//#else
import net.minecraft.client.gui.Gui;
//#endif

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//#if MC <= 11904
//$$import com.mojang.blaze3d.vertex.PoseStack;
//#elseif MC > 12006
import net.minecraft.client.DeltaTracker;
//#endif

//#if MC < 260000
//$$ import net.minecraft.client.gui.GuiGraphics;
//#endif

//#if MC >= 260200
//$$ @Mixin(Hud.class)
//#else
@Mixin(Gui.class)
//#endif
public abstract class MixinGui {

    //#if MC >= 260200
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    //#elseif MC >= 260100
    //$$ @Inject(method = "extractRenderState", at = @At("TAIL"))
    //#else
    //$$ @Inject(method = "render", at = @At("TAIL"))
    //#endif

    //#if MC > 12006
    private void hookRenderHud(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    //#elseif MC > 11904
    //$$ private void hookRenderHud(GuiGraphics guiGraphics, float f, CallbackInfo ci) {
    //#else
    //$$ private void hookRenderHud(PoseStack poseStack, float f, CallbackInfo ci) {
    //#endif
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isSpectator()
                || !HudVisibilityPolicy.shouldRender(
                        Configs.Core.WORK_SWITCH.getBooleanValue(),
                        Configs.Core.RENDER_HUD.getBooleanValue(),
                        Configs.Core.MISSING_MATERIAL_HUD.getBooleanValue()
                )) {
            return;
        }

        //#if MC > 11904
        Render2DUtils.initGuiGraphics(guiGraphics);
        //#else
        //$$ Render2DUtils.initMatrix(poseStack);
        //#endif

        float scaledWidth = mc.getWindow().getGuiScaledWidth();
        float scaledHeight = mc.getWindow().getGuiScaledHeight();
        Render2D.INSTANCE.render(scaledWidth, scaledHeight);
    }

}
