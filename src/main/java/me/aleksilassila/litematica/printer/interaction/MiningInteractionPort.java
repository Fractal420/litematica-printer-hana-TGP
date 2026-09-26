package me.aleksilassila.litematica.printer.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.item.ItemStack;

public interface MiningInteractionPort {
    Minecraft client();

    BlockPos destroyPos();

    void destroyPos(BlockPos pos);

    ItemStack destroyingItem();

    void destroyingItem(ItemStack stack);

    float destroyProgress();

    void destroyProgress(float progress);

    boolean isDestroying();

    void isDestroying(boolean destroying);

    boolean destroyBlock(BlockPos pos);

    boolean matchesDestroyTarget(BlockPos pos);

    boolean isInventorySwitchPending();

    void ensureCarriedItemSent();

    ServerboundPlayerActionPacket actionPacket(
            ServerboundPlayerActionPacket.Action action,
            BlockPos pos,
            Direction direction,
            int sequence
    );
}
