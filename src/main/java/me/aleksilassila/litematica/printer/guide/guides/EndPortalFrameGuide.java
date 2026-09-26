package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.item.Items;

public class EndPortalFrameGuide extends Guide {

    public EndPortalFrameGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        boolean requiredHasEye = getProperty(requiredState, EndPortalFrameBlock.HAS_EYE).orElseThrow();
        boolean currentHasEye = getProperty(currentState, EndPortalFrameBlock.HAS_EYE).orElseThrow();
        if (requiredHasEye && !currentHasEye) {
            return Result.success(new ClickAction().setItem(Items.ENDER_EYE));
        }
        return Result.SKIP;
    }
}
