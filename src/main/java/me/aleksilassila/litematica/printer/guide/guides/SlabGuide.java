package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.PrinterUtils;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.minecraft.DirectionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class SlabGuide extends Guide {

    private final SlabType slabType;

    public SlabGuide(SchematicBlockContext context) {
        super(context);
        this.slabType = getProperty(requiredState, SlabBlock.TYPE).orElseThrow();
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {

        if (slabType == SlabType.DOUBLE && state == BlockMatchResult.WRONG_STATE) {
            return Result.SKIP;
        }

        if (slabType == SlabType.DOUBLE && state == BlockMatchResult.MISSING) {

            Map<Direction, Vec3> slabSides = PrinterUtils.getSlabSides(level, blockPos, SlabType.BOTTOM);
            return Result.success(new Action().setSides(slabSides));
        }

        Map<Direction, Vec3> sides = new HashMap<>();
        Direction half;

        if (slabType == SlabType.TOP) {
            half = Direction.UP;
        } else if (slabType == SlabType.BOTTOM) {
            half = Direction.DOWN;
        } else {

            half = Direction.DOWN;
        }

        sides.put(half, Vec3.ZERO);

        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = blockPos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.hasProperty(SlabBlock.TYPE)) {
                SlabType neighborType = getProperty(neighborState, SlabBlock.TYPE).orElse(SlabType.BOTTOM);
                if (neighborType != SlabType.DOUBLE && neighborType != slabType) {
                    continue;
                }
            }
            sides.put(side, Vec3.atLowerCornerOf(DirectionUtils.getVector(half)).scale(0.25));
        }

        return Result.success(new Action().setSides(sides));
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {

        if (slabType == SlabType.DOUBLE) {
            if (currentState.hasProperty(SlabBlock.TYPE)) {
                SlabType current = getProperty(currentState, SlabBlock.TYPE).orElse(SlabType.BOTTOM);

                Direction clickFace = current == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
                return Result.success(new ClickAction()
                        .setSides(clickFace)
                        .setItem(requiredBlock.asItem()));
            }
        }

        return Result.SKIP;
    }
}
