package me.aleksilassila.litematica.printer.utils.mods;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.QuickShulkerModeType;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.utils.minecraft.MessageUtils;
import net.kyrptonaught.quickshulker.client.ClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

@SuppressWarnings({"DataFlowIssue", "SpellCheckingInspection"})
public class ShulkerUtils {
    static final Minecraft client = Minecraft.getInstance();
    private static boolean apiFailureLogged;

    public static boolean openShulker(ItemStack stack, int shulkerBoxSlot) {
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        IConfigOptionListEntry openMode = Configs.Placement.QUICK_SHULKER_MODE.getOptionListValue();
        if (openMode == QuickShulkerModeType.CLICK_SLOT) {
            client.gameMode.handleContainerInput(client.player.containerMenu.containerId, shulkerBoxSlot, 1, ContainerInput.PICKUP, client.player);
            return true;
        } else if (openMode == QuickShulkerModeType.INVOKE) {
            if (ModLoadUtils.isQuickShulkerLoaded()) {
                try {
                    return ClientUtil.CheckAndSend(stack, shulkerBoxSlot);
                } catch (Exception ignored) {
                    return false;
                } catch (LinkageError error) {
                    if (!apiFailureLogged) {
                        apiFailureLogged = true;
                        Reference.LOGGER.warn("Quick Shulker API 不兼容，已停用调用模组路径", error);
                    }
                    return false;
                }
            } else {
                MessageUtils.addMessage(I18n.SHULKER_MOD_NOT_LOADED.getName());
            }
        }
        return false;
    }
}
