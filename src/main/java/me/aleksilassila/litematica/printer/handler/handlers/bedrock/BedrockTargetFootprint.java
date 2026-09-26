package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

import java.util.LinkedHashSet;
import java.util.Set;

final class BedrockTargetFootprint {
    private final ClientLevel level;
    private final BlockPos bedrockPos;
    private final BlockPos pistonPos;
    private final BlockPos headPos;
    private final Set<BlockPos> temporary = new LinkedHashSet<>();

    BedrockTargetFootprint(
            ClientLevel level,
            BlockPos bedrockPos,
            BlockPos pistonPos,
            BlockPos headPos
    ) {
        this.level = level;
        this.bedrockPos = bedrockPos;
        this.pistonPos = pistonPos;
        this.headPos = headPos;
    }

    void recordTemporary(BlockPos pos) {
        if (pos != null) {
            this.temporary.add(pos);
        }
    }

    Set<BlockPos> cleanupPositions(
            BedrockTargetResidue residue,
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(this.pistonPos);
        positions.add(this.headPos);
        if (residue.hasCleanupResidue(torchSupportPos)) positions.add(torchSupportPos);
        if (slimePos != null) positions.add(slimePos);
        if (residue.hasCleanupResidue(torchPos)) positions.add(torchPos);
        positions.addAll(this.temporary);
        return positions;
    }

    Set<BlockPos> structuralPositions(
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(this.bedrockPos);
        positions.add(this.pistonPos);
        positions.add(this.headPos);
        for (BlockPos temporaryPos : this.temporary) {
            if (!isPowerPosition(temporaryPos, torchSupportPos, torchPos, slimePos)) {
                positions.add(temporaryPos);
            }
        }
        return positions;
    }

    Set<BlockPos> powerReservationPositions(
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        if (torchSupportPos != null) positions.add(torchSupportPos);
        if (torchPos != null) positions.add(torchPos);
        if (slimePos != null) positions.add(slimePos);
        return positions;
    }

    Set<BlockPos> reservedPositions(
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(this.bedrockPos);
        positions.add(this.pistonPos);
        positions.add(this.headPos);
        positions.addAll(this.powerReservationPositions(torchSupportPos, torchPos, slimePos));
        positions.addAll(this.temporary);
        return positions;
    }

    Set<BlockPos> staticMachinePositions(
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        positions.add(this.bedrockPos);
        positions.add(this.pistonPos);
        positions.add(this.headPos);
        positions.addAll(this.powerReservationPositions(torchSupportPos, torchPos, slimePos));
        return positions;
    }

    Set<BlockPos> machineFootprint(
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>(
                this.reservedPositions(torchSupportPos, torchPos, slimePos));
        positions.addAll(BedrockEnvironment.getTorchInfluencePositions(this.pistonPos));
        return positions;
    }

    Set<BlockPos> ownedTorchPositions(BlockPos torchPos) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        if (torchPos != null && BedrockEnvironment.isRedstoneTorchAt(this.level, torchPos)) {
            positions.add(torchPos);
        }
        return positions;
    }

    private static boolean isPowerPosition(
            BlockPos pos,
            BlockPos torchSupportPos,
            BlockPos torchPos,
            BlockPos slimePos
    ) {
        return pos != null
                && (pos.equals(torchSupportPos) || pos.equals(torchPos) || pos.equals(slimePos));
    }
}
