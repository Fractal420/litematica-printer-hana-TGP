package me.aleksilassila.litematica.printer.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

//#if MC >= 12106
import net.minecraft.client.renderer.RenderPipelines;
//#endif
//#if MC >= 12103 && MC < 12106
//$$ import net.minecraft.client.renderer.RenderType;
//#endif
//#if MC <= 11904
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import net.minecraft.client.gui.GuiComponent;
//#endif

import java.awt.*;

public class Render2DUtils {
    public static final Minecraft client = Minecraft.getInstance();
    private static PoseStack poseStack;
    private static GuiGraphicsExtractor guiGraphics;

    public static void initMatrix(PoseStack poseStack) {
        Render2DUtils.poseStack = poseStack;
    }

    public static void initGuiGraphics(GuiGraphicsExtractor guiGraphics) {
        Render2DUtils.guiGraphics = guiGraphics;
    }

    public static void ensureInitialized() {
        //#if MC > 11904
        if (guiGraphics == null) {
            throw new NullPointerException("GuiGraphics is null! Call initGuiGraphics first.");
        }
        //#else
        //$$ if (poseStack == null) {
        //$$     throw new NullPointerException("PoseStack is null! Call initMatrix first.");
        //$$ }
        //#endif
    }

    public static void drawString(String text, int x, int y, Color color, boolean withShadow) {
        drawString(text, x, y, color, withShadow, false);
    }

    public static void drawString(String text, int x, int y, Color color, boolean withShadow, boolean centered) {
        if (centered) {
            x -= client.font.width(text) / 2;
        }
        ensureInitialized();

        //#if MC > 11904
        guiGraphics.text(client.font, text, x, y, color.getRGB(), withShadow);
        //#else
        //$$ if (withShadow) {
        //$$    client.font.drawShadow(poseStack, text, x, y, color.getRGB());
        //$$ } else {
        //$$    client.font.draw(poseStack, text, x, y, color.getRGB());
        //$$ }
        //#endif
    }

    public static void fill(int x1, int y1, int x2, int y2, Color color) {
        ensureInitialized();
        //#if MC > 11904
        guiGraphics.fill(x1, y1, x2, y2, color.getRGB());
        //#else
        //$$ GuiComponent.fill(poseStack, x1, y1, x2, y2, color.getRGB());
        //#endif
    }

    public static void drawStringScaled(String text, int x, int y, Color color, boolean withShadow, float scale) {
        ensureInitialized();
        if (Math.abs(scale - 1.0F) < 0.001F) {
            drawString(text, x, y, color, withShadow);
            return;
        }
        pushPose();
        translate(x, y, 0.0D);
        scale(scale, scale, 1.0F);
        drawString(text, 0, 0, color, withShadow);
        popPose();
    }

    public static void pushPose() {
        ensureInitialized();
        //#if MC >= 12106
        guiGraphics.pose().pushMatrix();
        //#elseif MC > 11904
        //$$ guiGraphics.pose().pushPose();
        //#else
        //$$ poseStack.pushPose();
        //#endif
    }

    public static void popPose() {
        ensureInitialized();
        //#if MC >= 12106
        guiGraphics.pose().popMatrix();
        //#elseif MC > 11904
        //$$ guiGraphics.pose().popPose();
        //#else
        //$$ poseStack.popPose();
        //#endif
    }

    public static void translate(double x, double y, double z) {
        ensureInitialized();
        //#if MC >= 12106
        guiGraphics.pose().translate((float) x, (float) y);
        //#elseif MC > 11904
        //$$ guiGraphics.pose().translate((float) x, (float) y, (float) z);
        //#else
        //$$ poseStack.translate(x, y, z);
        //#endif
    }

    public static void scale(float x, float y, float z) {
        ensureInitialized();
        //#if MC >= 12106
        guiGraphics.pose().scale(x, y);
        //#elseif MC > 11904
        //$$ guiGraphics.pose().scale(x, y, z);
        //#else
        //$$ poseStack.scale(x, y, z);
        //#endif
    }

    public static void drawTexture(Identifier texture, int x, int y, int width, int height) {
        drawTexture(texture, x, y, 0, 0, width, height, width, height);
    }

    public static void drawTexture(Identifier texture, int x, int y,
                                   int u, int v, int regionWidth, int regionHeight,
                                   int textureWidth, int textureHeight) {
        ensureInitialized();

        //#if MC >= 12106
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                x, y,
                (float) u, (float) v,
                regionWidth, regionHeight,
                regionWidth, regionHeight,
                textureWidth, textureHeight
        );
        //#elseif MC >= 12103
        //$$ guiGraphics.blit(
        //$$         RenderType::guiTextured,
        //$$         texture,
        //$$         x, y,
        //$$         (float) u, (float) v,
        //$$         regionWidth, regionHeight,
        //$$         regionWidth, regionHeight,
        //$$         textureWidth, textureHeight
        //$$ );
        //#elseif MC > 11904
        //$$ guiGraphics.blit(texture, x, y, (float) u, (float) v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#else
        //$$ RenderSystem.setShaderTexture(0, texture);
        //$$ GuiComponent.blit(poseStack, x, y, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#endif
    }

    public static void drawItem(ItemStack stack, int x, int y) {
        ensureInitialized();
        //#if MC > 12111
        guiGraphics.fakeItem(stack, x, y);
        //#elseif MC > 11904
        //$$ guiGraphics.renderFakeItem(stack, x, y);
        //#elseif MC > 11802
        //$$ client.getItemRenderer().renderGuiItem(poseStack, stack, x, y);
        //#else
        //$$ client.getItemRenderer().renderGuiItem(stack, x, y);
        //#endif
    }

    public static void drawItemWithDecorations(ItemStack stack, int x, int y) {
        drawItem(stack, x, y);
        //#if MC > 12111
        guiGraphics.itemDecorations(client.font, stack, x, y);
        //#elseif MC > 11904
        //$$ guiGraphics.renderItemDecorations(client.font, stack, x, y);
        //#elseif MC > 11802
        //$$ client.getItemRenderer().renderGuiItemDecorations(poseStack, client.font, stack, x, y);
        //#else
        //$$ client.getItemRenderer().renderGuiItemDecorations(client.font, stack, x, y);
        //#endif
    }

    public static void drawItemWithLabel(ItemStack stack, int x, int y, String text, Color color, boolean shadow) {
        drawItem(stack, x, y);
        drawString(text, x + 20, y + 5, color, shadow);
    }

    public static void drawIconWithLabel(Identifier texture, int x, int y,
                                         int iconWidth, int iconHeight,
                                         String text, Color color, boolean shadow) {
        drawTexture(texture, x, y, iconWidth, iconHeight);
        int textY = y + (iconHeight - client.font.lineHeight) / 2;
        drawString(text, x + iconWidth + 4, textY, color, shadow);
    }

    public static void drawBlock(Block block, int x, int y) {
        drawItem(block.asItem().getDefaultInstance(), x, y);
    }

    public static void drawBlockWithDecorations(Block block, int x, int y) {
        drawItemWithDecorations(block.asItem().getDefaultInstance(), x, y);
    }

    public static void drawBlockState(BlockState state, int x, int y) {
        drawBlock(state.getBlock(), x, y);
    }
}
