package me.aleksilassila.litematica.printer.utils.mods;

import net.fabricmc.loader.api.FabricLoader;

public class ModLoadUtils {
    public static int closeScreen = 0;

    public static boolean isLoadMod(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    public static boolean isChestTrackerLoaded(){
        return isLoadMod("chesttracker");
    }

    public static boolean isQuickShulkerLoaded(){
        return isLoadMod("quickshulker");
    }

    public static boolean isTweakerooLoaded() {
        return isLoadMod("tweakeroo");
    }
}
