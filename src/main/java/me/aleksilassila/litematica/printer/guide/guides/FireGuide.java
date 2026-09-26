package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.PrinterUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.item.Items;

public class FireGuide extends Guide {

    public FireGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        return Result.success(new Action()
                .setSides(findFireDirection())
                .setItems(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)
                .setRequiresSupport());
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {

        if (!getProperty(requiredState, FireBlock.AGE).equals(getProperty(currentState, FireBlock.AGE))) {
            return Result.SKIP;
        }

        if (requiredBlock instanceof SoulFireBlock) {
            return Result.SKIP;
        }

        return Result.PASS;
    }

    private Direction findFireDirection() {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            Object value = PrinterUtils.getPropertyByName(requiredState, direction.name());
            if (value instanceof Boolean && (Boolean) value) {
                return direction;
            }
        }
        return Direction.DOWN;
    }
}
