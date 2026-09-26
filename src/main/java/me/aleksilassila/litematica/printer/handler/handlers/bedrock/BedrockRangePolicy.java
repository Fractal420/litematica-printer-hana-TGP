package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.RadiusShapeType;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.minecraft.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.client.player.LocalPlayer;

final class BedrockRangePolicy {
    private static final double INTERACTION_GRACE = 1.0D;

    private BedrockRangePolicy() {
    }

    static boolean canInteract(BlockPos pos) {
        if (pos == null || !isWithinWorkRange(pos)) {
            return false;
        }
        if (!Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue()) {
            return true;
        }
        LocalPlayer player = ConfigUtils.client.player;
        return player != null && PlayerUtils.isWithinBlockInteractionRange(player, pos, INTERACTION_GRACE);
    }

    static BlockPos findFirstOutOfRange(BlockPos... positions) {
        if (positions == null) {
            return null;
        }
        for (BlockPos pos : positions) {
            if (pos != null && !canInteract(pos)) {
                return pos;
            }
        }
        return null;
    }

    static BlockPos findFirstOutOfRange(Iterable<BlockPos> positions) {
        if (positions == null) {
            return null;
        }
        for (BlockPos pos : positions) {
            if (pos != null && !canInteract(pos)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean isWithinWorkRange(BlockPos pos) {
        double workRange = ConfigUtils.getWorkRange();
        if (Configs.Core.ITERATOR_SHAPE.getOptionListValue() instanceof RadiusShapeType shape) {
            return switch (shape) {
                case SPHERE -> PlayerUtils.isWithinWorkInteractedEuclideanRange(pos, workRange);
                case OCTAHEDRON -> PlayerUtils.isWithinWorkInteractedManhattanRange(pos, workRange);
                case CUBE -> PlayerUtils.isWithinWorkInteractedCubeRange(pos, workRange);
            };
        }
        return true;
    }
}
