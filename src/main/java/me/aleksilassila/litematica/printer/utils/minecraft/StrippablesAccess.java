package me.aleksilassila.litematica.printer.utils.minecraft;

import net.minecraft.world.level.block.Block;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

public final class StrippablesAccess {
    private static Map<Block, Block> cached;

    private StrippablesAccess() {
    }

    public static Map<Block, Block> getStrippables() {
        //#if MC >= 260300
        //$$ return getFromRegistryNaming();
        //#else
        return net.fabricmc.fabric.mixin.content.registry.AxeItemAccessor.getStrippables();
        //#endif
    }

    //#if MC >= 260300
    //$$ private static Map<Block, Block> getFromRegistryNaming() {
    //$$     if (cached != null) {
    //$$         return cached;
    //$$     }
    //$$     Map<Block, Block> map = new IdentityHashMap<>();
    //$$     for (Block stripped : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
    //$$         net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(stripped);
    //$$         if (id == null) {
    //$$             continue;
    //$$         }
    //$$         String path = id.getPath();
    //$$         if (!path.startsWith("stripped_")) {
    //$$             continue;
    //$$         }
    //$$         net.minecraft.resources.Identifier baseId = net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring("stripped_".length()));
    //$$         Block base = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(baseId).orElse(null);
    //$$         if (base != null && base != stripped) {
    //$$             map.put(base, stripped);
    //$$         }
    //$$     }
    //$$     cached = Collections.unmodifiableMap(map);
    //$$     return cached;
    //$$ }
    //#endif
}
