package me.aleksilassila.litematica.printer.integration.litematica;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.utils.mods.LitematicaUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import fi.dy.masa.litematica.world.WorldSchematic;

import java.util.List;
import java.util.function.Predicate;

public final class LitematicaAdapter {
    public WorldSchematic schematicWorld() {
        return SchematicWorldHandler.getSchematicWorld();
    }

    public boolean isSchematicBlock(BlockPos pos) {
        return LitematicaUtils.isSchematicBlock(pos);
    }

    public List<PrinterBox> createSelectionBoxes() {
        return LitematicaUtils.createSelection1Boxes();
    }

    public List<PrinterBox> createSchematicPlacementBoxes() {
        return LitematicaUtils.createSchematicPlacementBoxes();
    }

    public boolean isWithinSelectionRange(BlockPos pos) {
        return LitematicaUtils.isWithinSelection1ModeRange(pos);
    }

    public Predicate<BlockPos> selectionRangePredicate() {
        return LitematicaUtils.createSelection1RangePredicate();
    }

    public boolean isPositionWithinRenderLayer(BlockPos pos) {
        return LitematicaUtils.isPositionWithinRange(pos);
    }

    public PrinterBox clampToRenderLayer(PrinterBox box) {
        return LitematicaUtils.clampToRenderLayer(box);
    }

    public Vec3 usePrecisionPlacement(BlockPos pos, BlockState state) {
        return LitematicaUtils.usePrecisionPlacement(pos, state);
    }
}
