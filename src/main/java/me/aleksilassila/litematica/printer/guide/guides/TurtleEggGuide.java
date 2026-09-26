package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.item.Items;

public class TurtleEggGuide extends Guide {

    public TurtleEggGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {

        return Result.success(new Action()
                .setSides(Direction.DOWN)
                .setRequiresSupport());
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        int currentEggs = getProperty(currentState, TurtleEggBlock.EGGS).orElseThrow();
        int requiredEggs = getProperty(requiredState, TurtleEggBlock.EGGS).orElseThrow();

        if (currentEggs < requiredEggs) {
            return Result.success(new ClickAction().setItem(Items.TURTLE_EGG));
        }

        return Result.SKIP;
    }
}
