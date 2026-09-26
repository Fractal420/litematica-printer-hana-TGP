package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.item.Items;

public class CandleGuide extends Guide {

    public CandleGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        int requiredCandles = getProperty(requiredState, CandleBlock.CANDLES).orElseThrow();
        int currentCandles = getProperty(currentState, CandleBlock.CANDLES).orElseThrow();
        boolean requiredLit = getProperty(requiredState, CandleBlock.LIT).orElseThrow();
        boolean currentLit = getProperty(currentState, CandleBlock.LIT).orElseThrow();

        if (currentCandles < requiredCandles) {
            return Result.success(new ClickAction().setItem(requiredBlock.asItem()));
        }

        if (!currentLit && requiredLit) {
            return Result.success(new ClickAction().setItems(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE));
        }

        if (currentLit && !requiredLit) {
            return Result.success(new ClickAction());
        }

        return Result.SKIP;
    }
}
