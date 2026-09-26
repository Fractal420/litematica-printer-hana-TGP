package me.aleksilassila.litematica.printer.interaction;

import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

public interface StrippableBlockPort {
    @Nullable Block sourceFor(Block strippedBlock);
}
