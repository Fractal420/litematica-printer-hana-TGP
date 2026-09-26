package me.aleksilassila.litematica.printer.printer.action;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClickAction extends Action {
    @Override
    public boolean queueAction(
            @NotNull ActionPort actionBroker,
            @NotNull BlockPos blockPos,
            @NotNull Direction side,
            boolean useShift,
            @NotNull LocalPlayer player,
            @Nullable Item[] expectedItems
    ) {
        return actionBroker.queueClick(
                blockPos,
                side,
                getSides().get(side),
                false,
                this.clickRepeatCount,
                expectedItems,
                ActionPort.ActionSource.PRINT
        );
    }

    @Override
    public @Nullable Item[] getRequiredItems(Block backup) {
        return this.clickItems;
    }

    @Override
    public @Nullable Direction getValidSide(ClientLevel world, BlockPos pos) {
        for (Direction side : getOrderedSides()) {
            return side;
        }
        return null;
    }
}
