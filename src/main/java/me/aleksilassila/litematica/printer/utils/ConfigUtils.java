package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.config.options.ConfigOptionList;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.*;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.utils.minecraft.PlayerUtils;
import me.aleksilassila.litematica.printer.utils.mods.LitematicaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class ConfigUtils {
    @NotNull
    public static final Minecraft client = Minecraft.getInstance();

    public static boolean isEnable() {
        return Configs.Core.WORK_SWITCH.getBooleanValue();
    }

    public static boolean isMultiMode() {
        return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI);
    }

    public static boolean isSingleMode() {
        return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.SINGLE);
    }

    public static boolean isPrintMode() {
        if (isMultiMode()) {
            return Configs.Core.PRINT.getBooleanValue();
        }
        return Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.PRINTER;
    }

    public static boolean isMineMode() {
        if (isMultiMode()) {
            return Configs.Core.MINE.getBooleanValue();
        }
        return Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.MINE;
    }

    public static boolean isFillMode() {
        if (isMultiMode()) {
            return Configs.Core.FILL.getBooleanValue();
        }
        return Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.FILL;
    }

    public static boolean isFluidMode() {
        if (isMultiMode()) {
            return Configs.Core.FLUID.getBooleanValue();
        }
        return Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.FLUID;
    }

    public static boolean isCoverMode() {
        if (isMultiMode()) {
            return Configs.Core.COVER.getBooleanValue();
        }
        return Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.COVER;
    }

    public static boolean isBedrockMode() {
        if (isMultiMode()) {
            return Configs.Hotkeys.BEDROCK.getBooleanValue();
        }
        return Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.BEDROCK;
    }

    public static PrintModeType getPrintModeType() {
        return (PrintModeType) Configs.Core.WORK_MODE_TYPE.getOptionListValue();
    }

    public static int getPlaceCooldown() {
        return Configs.Placement.PLACE_COOLDOWN.getIntegerValue();
    }

    public static int getBreakCooldown() {
        return Configs.Break.BREAK_COOLDOWN.getIntegerValue();
    }

    public static int getWorkRange() {
        return Configs.Core.WORK_RANGE.getIntegerValue();
    }

    public static boolean canInteracted(BlockPos blockPos) {
        double workRange = getWorkRange();
        if (Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue()) {
            if (client.player != null && !PlayerUtils.isWithinBlockInteractionRange(client.player, blockPos, 1F)) {
                return false;
            }
        }
        if (Configs.Core.ITERATOR_SHAPE.getOptionListValue() instanceof RadiusShapeType radiusShapeType) {
            return switch (radiusShapeType) {
                case SPHERE -> PlayerUtils.isWithinWorkInteractedEuclideanRange(blockPos, workRange);
                case OCTAHEDRON -> PlayerUtils.isWithinWorkInteractedManhattanRange(blockPos, workRange);
                case CUBE -> PlayerUtils.isWithinWorkInteractedCubeRange(blockPos, workRange);
            };
        }
        return true;
    }

    public static Predicate<BlockPos> createCanInteractPredicate() {
        LocalPlayer player = client.player;
        if (player == null) {
            return pos -> false;
        }

        double workRange = getWorkRange();
        double workRangeSqr = workRange * workRange;
        Vec3 eye = player.getEyePosition();
        double eyeX = eye.x;
        double eyeY = eye.y;
        double eyeZ = eye.z;
        //#if MC > 11802
        double interactionEyeY = eyeY;
        //#else
        //$$ double interactionEyeY = player.getY() + 1.5D;
        //#endif
        BlockPos playerBlockPos = player.blockPosition();
        int playerBlockX = playerBlockPos.getX();
        int playerBlockY = playerBlockPos.getY();
        int playerBlockZ = playerBlockPos.getZ();
        boolean checkInteractionRange = Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue();
        double interactionRange = PlayerUtils.getPlayerBlockInteractionRange(5) + 1.0D;
        double interactionRangeSqr = interactionRange * interactionRange;
        RadiusShapeType shape = Configs.Core.ITERATOR_SHAPE.getOptionListValue() instanceof RadiusShapeType value
                ? value
                : null;

        return pos -> {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (checkInteractionRange) {
                //#if MC > 12006
                double dx = Math.max(Math.max(x - eyeX, eyeX - (x + 1.0D)), 0.0D);
                double dy = Math.max(Math.max(y - eyeY, eyeY - (y + 1.0D)), 0.0D);
                double dz = Math.max(Math.max(z - eyeZ, eyeZ - (z + 1.0D)), 0.0D);
                if (dx * dx + dy * dy + dz * dz >= interactionRangeSqr) {
                    return false;
                }
                //#else
                //$$ double dx = eyeX - (x + 0.5D);
                //$$ double dy = interactionEyeY - (y + 0.5D);
                //$$ double dz = eyeZ - (z + 0.5D);
                //$$ if (dx * dx + dy * dy + dz * dz > interactionRangeSqr) {
                //$$     return false;
                //$$ }
                //#endif
            }

            if (shape == null) {
                return true;
            }
            return switch (shape) {
                case SPHERE -> {
                    double dx = eyeX - (x + 0.5D);
                    double dy = eyeY - (y + 0.5D);
                    double dz = eyeZ - (z + 0.5D);
                    yield dx * dx + dy * dy + dz * dz <= workRangeSqr;
                }
                case OCTAHEDRON -> Math.abs(x - playerBlockX)
                        + Math.abs(y - playerBlockY)
                        + Math.abs(z - playerBlockZ) <= workRange;
                case CUBE -> Math.abs(x - playerBlockX) <= workRange
                        && Math.abs(y - playerBlockY) <= workRange
                        && Math.abs(z - playerBlockZ) <= workRange;
            };
        };
    }

    public static boolean isPositionInSelectionRange(Player player, @NotNull BlockPos pos, ConfigOptionList selectionTypeConfig) {
        if (player == null || selectionTypeConfig == null) {
            return false;
        }
        if (!(selectionTypeConfig.getOptionListValue() instanceof SelectionType selectionType)) {
            return false;
        }
        return switch (selectionType) {
            case LITEMATICA_RENDER_LAYER -> LitematicaUtils.isPositionWithinRange(pos);
            case LITEMATICA_SELECTION_BELOW_PLAYER -> pos.getY() <= standingBlockY(player);
            case LITEMATICA_SELECTION_BELOW_PLAYER_LAYER ->
                    LitematicaUtils.isPositionWithinRange(pos) && pos.getY() <= standingBlockY(player);
            case LITEMATICA_SELECTION_ABOVE_PLAYER -> pos.getY() > standingBlockY(player);
            case LITEMATICA_SELECTION_ABOVE_PLAYER_LAYER ->
                    LitematicaUtils.isPositionWithinRange(pos) && pos.getY() > standingBlockY(player);
            default -> true;
        };
    }

    public static int standingBlockY(Player player) {
        return player.getBlockY() - 1;
    }

    public static boolean isPrintBreakEnabled() {
        return Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
                || Configs.Print.BREAK_EXTRA_BLOCK.getBooleanValue()
                || Configs.Print.BREAK_WRONG_STATE_BLOCK.getBooleanValue();
    }

    public static boolean isPositionInMineSelectionRange(Player player, @NotNull BlockPos pos) {
        return isPositionInSelectionRange(player, pos, Configs.Mine.MINE_SELECTION_TYPE);
    }

    public static @Nullable PrinterBox clampBoxToSelection(
            @Nullable PrinterBox box,
            Player player,
            ConfigOptionList selectionTypeConfig
    ) {
        if (box == null || selectionTypeConfig == null) {
            return box;
        }
        if (!(selectionTypeConfig.getOptionListValue() instanceof SelectionType selectionType)) {
            return null;
        }
        return switch (selectionType) {
            case LITEMATICA_SELECTION -> box;
            case LITEMATICA_RENDER_LAYER -> LitematicaUtils.clampToRenderLayer(box);
            case LITEMATICA_SELECTION_BELOW_PLAYER, LITEMATICA_SELECTION_BELOW_PLAYER_LAYER -> {
                if (player == null) yield null;
                PrinterBox base = selectionType.requiresRenderLayer()
                        ? LitematicaUtils.clampToRenderLayer(box)
                        : box;
                if (base == null) yield null;
                yield clipMaximumY(base, standingBlockY(player));
            }
            case LITEMATICA_SELECTION_ABOVE_PLAYER, LITEMATICA_SELECTION_ABOVE_PLAYER_LAYER -> {
                if (player == null) yield null;
                PrinterBox base = selectionType.requiresRenderLayer()
                        ? LitematicaUtils.clampToRenderLayer(box)
                        : box;
                if (base == null) yield null;
                yield clipMinimumY(base, standingBlockY(player) + 1);
            }
        };
    }

    public static List<PrinterBox> buildClampedSelectionBoxes(
            List<PrinterBox> baseBoxes,
            PrinterBox interactionBox,
            Player player,
            ConfigOptionList selectionTypeConfig
    ) {
        if (interactionBox == null || baseBoxes == null || baseBoxes.isEmpty()) {
            return List.of();
        }
        List<PrinterBox> result = new ArrayList<>(baseBoxes.size());
        for (PrinterBox baseBox : baseBoxes) {
            PrinterBox bounded = intersect(interactionBox, baseBox);
            bounded = clampBoxToSelection(bounded, player, selectionTypeConfig);
            if (bounded != null) {
                result.add(bounded);
            }
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    public static List<PrinterBox> unionPrinterBoxes(List<PrinterBox> first, List<PrinterBox> second) {
        if (first == null || first.isEmpty()) {
            return second == null || second.isEmpty() ? List.of() : second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        List<PrinterBox> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
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

    public static Direction getFillModeFacing() {
        if (Configs.Fill.FILL_BLOCK_FACING.getOptionListValue() instanceof FillModeFacingType fillModeFacingType) {
            return switch (fillModeFacingType) {
                case DOWN -> Direction.DOWN;
                case UP -> Direction.UP;
                case WEST -> Direction.WEST;
                case EAST -> Direction.EAST;
                case NORTH -> Direction.NORTH;
                case SOUTH -> Direction.SOUTH;
                default -> null;
            };
        }
        return null;
    }

    public static float getBreakProgressThreshold() {
        int value = Configs.Break.BREAK_PROGRESS_THRESHOLD.getIntegerValue();
        if (value < 70) {
            value = 70;
        } else if (value > 100) {
            value = 100;
        }
        return (float) value / 100;
    }

}
