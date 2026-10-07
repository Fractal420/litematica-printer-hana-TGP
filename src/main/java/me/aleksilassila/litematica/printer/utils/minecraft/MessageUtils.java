package me.aleksilassila.litematica.printer.utils.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public class MessageUtils {
    public static void setOverlayMessage(Component message, boolean bl) {
        //#if MC >= 260100
        Minecraft.getInstance().gui.setOverlayMessage(message, bl);
        //#else
        //$$ Minecraft.getInstance().gui.setOverlayMessage(message, bl);
        //#endif
    }

    public static void setOverlayMessage(Component message) {
        setOverlayMessage(message, false);
    }

    public static void setOverlayMessage(String message) {
        setOverlayMessage(StringUtils.literal(message));
    }

    public static void addMessage(Component message) {
        //#if MC >= 260100
        Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
        //#else
        //$$ LocalPlayer player = Minecraft.getInstance().player;
        //$$ if (player != null) {
        //$$     player.displayClientMessage(message, false);
        //$$     return;
        //$$ }
        //$$ try {
        //$$     Minecraft.getInstance().gui.getChat().addMessage(message);
        //$$ } catch (Throwable ignored) {
        //$$ }
        //#endif
    }

    public static void addMessage(String message) {
        addMessage(StringUtils.literal(message));
    }
}
