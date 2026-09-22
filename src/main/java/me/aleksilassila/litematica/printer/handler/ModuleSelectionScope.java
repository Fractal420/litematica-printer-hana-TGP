package me.aleksilassila.litematica.printer.handler;

import fi.dy.masa.malilib.config.options.ConfigOptionList;
import me.aleksilassila.litematica.printer.enums.SelectionType;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Converts Litematica selections and render layers into a cached scan scope. */
final class ModuleSelectionScope {
    private final FeatureModuleBase owner;
    @Nullable private final ConfigOptionList selectionConfig;
    @Nullable private PrinterBox cachedInput;
    @Nullable private SelectionType cachedSelectionType;
    private int cachedStandingY = Integer.MIN_VALUE;
    private List<PrinterBox> cachedBoxes = List.of();

    ModuleSelectionScope(FeatureModuleBase owner, @Nullable ConfigOptionList selectionConfig) {
        this.owner = owner;
        this.selectionConfig = selectionConfig;
    }

    void clearCache() {
        this.cachedInput = null;
        this.cachedSelectionType = null;
        this.cachedStandingY = Integer.MIN_VALUE;
        this.cachedBoxes = List.of();
    }

    @Nullable PrinterBox enclosingBox(PrinterBox interactionBox) {
        PrinterBox result = null;
        for (PrinterBox box : this.boxes(interactionBox)) {
            result = result == null ? box : new PrinterBox(
                    Math.min(result.minX, box.minX), Math.min(result.minY, box.minY),
                    Math.min(result.minZ, box.minZ), Math.max(result.maxX, box.maxX),
                    Math.max(result.maxY, box.maxY), Math.max(result.maxZ, box.maxZ));
        }
        return result;
    }

    List<PrinterBox> boxes(PrinterBox interactionBox) {
        if (interactionBox == null) return List.of();

        SelectionType currentType = this.currentSelectionType();
        int currentStandingY = this.standingYForCache(currentType);
        boolean cacheValid = interactionBox.equals(this.cachedInput)
                && currentType == this.cachedSelectionType
                && currentStandingY == this.cachedStandingY;
        if (cacheValid) return this.cachedBoxes;

        List<PrinterBox> baseBoxes;
        if (this.owner.isSchematicBlockHandler()) {
            baseBoxes = this.owner.litematica.createSchematicPlacementBoxes();
        } else if (this.owner.requiresSelection1ModeRangeCheck()) {
            baseBoxes = this.owner.litematica.createSelectionBoxes();
        } else {
            baseBoxes = List.of(interactionBox);
        }
        List<PrinterBox> result = new ArrayList<>(baseBoxes.size());
        for (PrinterBox baseBox : baseBoxes) {
            PrinterBox bounded = intersect(interactionBox, baseBox);
            bounded = this.clampToConfiguredSelection(bounded);
            if (bounded != null) result.add(bounded);
        }
        this.cachedInput = interactionBox;
        this.cachedSelectionType = currentType;
        this.cachedStandingY = currentStandingY;
        this.cachedBoxes = result.isEmpty() ? List.of() : List.copyOf(result);
        return this.cachedBoxes;
    }

    boolean contains(BlockPos pos) {
        if (!this.owner.isSchematicBlockHandler()
                && this.owner.requiresSelection1ModeRangeCheck()
                && !this.owner.litematica.isWithinSelectionRange(pos)) {
            return false;
        }
        return this.selectionConfig == null
                || ConfigUtils.isPositionInSelectionRange(this.owner.player, pos, this.selectionConfig);
    }

    Predicate<BlockPos> predicate() {
        Predicate<BlockPos> selection1 = this.owner.isSchematicBlockHandler()
                || !this.owner.requiresSelection1ModeRangeCheck()
                ? pos -> true : this.owner.litematica.selectionRangePredicate();
        Predicate<BlockPos> configured = this.configuredPredicate();
        return pos -> selection1.test(pos) && configured.test(pos);
    }

    private Predicate<BlockPos> configuredPredicate() {
        if (this.selectionConfig == null) return pos -> true;
        if (!(this.selectionConfig.getOptionListValue() instanceof SelectionType selectionType)) {
            return pos -> false;
        }
        LocalPlayer player = this.owner.player;
        return switch (selectionType) {
            case LITEMATICA_SELECTION -> pos -> true;
            case LITEMATICA_RENDER_LAYER -> this.owner.litematica::isPositionWithinRenderLayer;
            case LITEMATICA_SELECTION_BELOW_PLAYER -> {
                if (player == null) yield pos -> false;
                int standingY = standingBlockY(player);
                yield pos -> this.owner.litematica.isPositionWithinRenderLayer(pos)
                        && pos.getY() <= standingY;
            }
            case LITEMATICA_SELECTION_ABOVE_PLAYER -> {
                if (player == null) yield pos -> false;
                int standingY = standingBlockY(player);
                yield pos -> this.owner.litematica.isPositionWithinRenderLayer(pos)
                        && pos.getY() > standingY;
            }
        };
    }

    private @Nullable PrinterBox clampToConfiguredSelection(@Nullable PrinterBox box) {
        if (box == null || this.selectionConfig == null) return box;
        if (!(this.selectionConfig.getOptionListValue() instanceof SelectionType selectionType)) return null;
        LocalPlayer player = this.owner.player;
        return switch (selectionType) {
            case LITEMATICA_SELECTION -> box;
            case LITEMATICA_RENDER_LAYER -> this.owner.litematica.clampToRenderLayer(box);
            case LITEMATICA_SELECTION_BELOW_PLAYER -> {
                if (player == null) yield null;
                PrinterBox layerClamped = this.owner.litematica.clampToRenderLayer(box);
                if (layerClamped == null) yield null;
                yield clipMaximumY(layerClamped, standingBlockY(player));
            }
            case LITEMATICA_SELECTION_ABOVE_PLAYER -> {
                if (player == null) yield null;
                PrinterBox layerClamped = this.owner.litematica.clampToRenderLayer(box);
                if (layerClamped == null) yield null;
                yield clipMinimumY(layerClamped, standingBlockY(player) + 1);
            }
        };
    }

    @Nullable
    private SelectionType currentSelectionType() {
        if (this.selectionConfig == null) return null;
        if (this.selectionConfig.getOptionListValue() instanceof SelectionType type) return type;
        return null;
    }

    private int standingYForCache(@Nullable SelectionType type) {
        if (type != SelectionType.LITEMATICA_SELECTION_BELOW_PLAYER
                && type != SelectionType.LITEMATICA_SELECTION_ABOVE_PLAYER) {
            return Integer.MIN_VALUE;
        }
        LocalPlayer player = this.owner.player;
        return player == null ? Integer.MIN_VALUE : standingBlockY(player);
    }

    private static int standingBlockY(LocalPlayer player) {
        return player.getBlockY() - 1;
    }

    private static @Nullable PrinterBox clipMaximumY(PrinterBox box, int maxY) {
        int clipped = Math.min(box.maxY, maxY);
        return clipped < box.minY ? null
                : new PrinterBox(box.minX, box.minY, box.minZ, box.maxX, clipped, box.maxZ);
    }

    private static @Nullable PrinterBox clipMinimumY(PrinterBox box, int minY) {
        int clipped = Math.max(box.minY, minY);
        return clipped > box.maxY ? null
                : new PrinterBox(box.minX, clipped, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static @Nullable PrinterBox intersect(PrinterBox first, PrinterBox second) {
        int minX = Math.max(first.minX, second.minX), minY = Math.max(first.minY, second.minY);
        int minZ = Math.max(first.minZ, second.minZ), maxX = Math.min(first.maxX, second.maxX);
        int maxY = Math.min(first.maxY, second.maxY), maxZ = Math.min(first.maxZ, second.maxZ);
        return minX > maxX || minY > maxY || minZ > maxZ ? null
                : new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
