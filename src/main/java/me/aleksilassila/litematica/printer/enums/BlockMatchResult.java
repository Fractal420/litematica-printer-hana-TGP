package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.minecraft.BlockStateUtils;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.properties.Property;

public enum BlockMatchResult {

    MISSING,

    WRONG_BLOCK,

    WRONG_STATE,

    CORRECT;

    public static BlockMatchResult compare(SchematicBlockContext context, Property<?>... propertiesToIgnore) {
        if (context.requiredState.equals(context.currentState)) {
            return CORRECT;
        }
        if (context.requiredState.getBlock().equals(context.currentState.getBlock())) {
            if (BlockStateUtils.statesEqualIgnoreProperties(context.requiredState, context.currentState, propertiesToIgnore)) {
                return CORRECT;
            }
            return WRONG_STATE;
        }
        if (!context.requiredState.isAir() && BlockStateUtils.isReplaceable(context.currentState)) {
            return MISSING;
        }
        return WRONG_BLOCK;
    }
}

