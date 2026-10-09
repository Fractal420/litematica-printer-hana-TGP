package me.aleksilassila.litematica.printer.integration.inventory;

import me.aleksilassila.litematica.printer.interaction.StrippableBlockPort;
import me.aleksilassila.litematica.printer.utils.minecraft.StrippablesAccess;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class StrippableBlockAdapter implements StrippableBlockPort {
    private final Map<Block, Block> sourceByStripped = new IdentityHashMap<>();

    public StrippableBlockAdapter() {
        for (Map.Entry<Block, Block> entry : StrippablesAccess.getStrippables().entrySet()) {
            this.sourceByStripped.putIfAbsent(entry.getValue(), entry.getKey());
        }
    }

    @Override
    public @Nullable Block sourceFor(Block strippedBlock) {
        return this.sourceByStripped.get(strippedBlock);
    }
}
