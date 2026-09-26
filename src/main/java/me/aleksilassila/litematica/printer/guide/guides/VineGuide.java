package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.PrinterUtils;
import net.minecraft.core.Direction;

public class VineGuide extends Guide {

    public VineGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN && requiredBlock instanceof net.minecraft.world.level.block.VineBlock) continue;
            Object value = PrinterUtils.getPropertyByName(requiredState, direction.name());
            if (value instanceof Boolean && (Boolean) value) {
                return Result.success(new Action().setSides(direction));
            }
        }
        return Result.SKIP;
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN && requiredBlock instanceof net.minecraft.world.level.block.VineBlock) continue;
            Object value = PrinterUtils.getPropertyByName(requiredState, direction.name());
            if (value instanceof Boolean && (Boolean) value) {
                return Result.success(new Action().setSides(direction).setLookDirection(direction));
            }
        }
        return Result.SKIP;
    }
}
