package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public class ChestGuide extends Guide {

    public ChestGuide(SchematicBlockContext context) {
        super(context);
    }

    @Override
    protected Result onBuildActionMissingBlock(BlockMatchResult state) {
        Direction facing = getProperty(requiredState, ChestBlock.FACING).orElseThrow();

        Direction facingOpposite = facing.getOpposite();
        ChestType chestType = getProperty(requiredState, BlockStateProperties.CHEST_TYPE).orElse(ChestType.SINGLE);

        Map<Direction, Vec3> noChestSides = new HashMap<>();
        for (Direction side : Direction.values()) {
            if (level.getBlockState(blockPos.relative(side)).getBlock() instanceof ChestBlock) {
                continue;
            }
            noChestSides.put(side, Vec3.ZERO);
        }

        if (chestType == ChestType.SINGLE) {

            boolean hasChestNeighbor = Direction.Plane.HORIZONTAL.stream()
                    .anyMatch(s -> !noChestSides.containsKey(s));
            if (hasChestNeighbor) {
                return Result.success(new Action().setLookDirection(facingOpposite).setShift());
            }
            return Result.success(new Action().setSides(noChestSides).setLookDirection(facingOpposite));
        }

        Direction partnerDir = chestType == ChestType.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();

        Map<Direction, Vec3> clickSides = new HashMap<>(noChestSides);
        clickSides.put(partnerDir, Vec3.ZERO);

        return Result.success(new Action()
                .setSides(clickSides)
                .setLookDirection(facingOpposite)
                .setShift(false));
    }

    @Override
    protected Result onBuildActionWrongState(BlockMatchResult state) {
        return Result.SKIP;
    }
}
