package me.aleksilassila.litematica.printer.utils.minecraft;

import net.minecraft.world.entity.EntityType;
//#if MC > 260100
//$$ import net.minecraft.world.entity.EntityTypes;
//#endif
import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertSame;

class SpawnCheckUtilsTest {
    @Test
    void resolvesEntityTypesByStableRegistryId() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Method resolver = SpawnCheckUtils.class.getDeclaredMethod("resolveEntityType", String.class);
        resolver.setAccessible(true);

        //#if MC > 260100
        //$$ assertSame(EntityTypes.CREEPER, resolver.invoke(null, "minecraft:creeper"));
        //$$ assertSame(EntityTypes.WITHER_SKELETON, resolver.invoke(null, "minecraft:wither_skeleton"));
        //#else
        assertSame(EntityType.CREEPER, resolver.invoke(null, "minecraft:creeper"));
        assertSame(EntityType.WITHER_SKELETON, resolver.invoke(null, "minecraft:wither_skeleton"));
        //#endif
    }
}
