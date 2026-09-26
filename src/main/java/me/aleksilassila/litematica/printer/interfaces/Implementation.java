package me.aleksilassila.litematica.printer.interfaces;

import net.minecraft.world.level.block.*;

public class Implementation {

    public static Class<?>[] interactiveBlocks = {
            AbstractFurnaceBlock.class,
            CraftingTableBlock.class,
            ChestBlock.class,
            LeverBlock.class,
            DoorBlock.class,
            TrapDoorBlock.class,
            BedBlock.class,
            RedStoneWireBlock.class,
            ScaffoldingBlock.class,
            HopperBlock.class,
            EnchantingTableBlock.class,
            NoteBlock.class,
            JukeboxBlock.class,
            CakeBlock.class,
            FenceGateBlock.class,
            BrewingStandBlock.class,
            DragonEggBlock.class,
            CommandBlock.class,
            BeaconBlock.class,
            AnvilBlock.class,
            ComparatorBlock.class,
            RepeaterBlock.class,
            DropperBlock.class,
            DispenserBlock.class,
            ShulkerBoxBlock.class,
            LecternBlock.class,
            FlowerPotBlock.class,
            BarrelBlock.class,
            BellBlock.class,
            SmithingTableBlock.class,
            LoomBlock.class,
            CartographyTableBlock.class,
            GrindstoneBlock.class,
            StonecutterBlock.class,
            //#if MC < 12109
            //$$ FletchingTableBlock.class, // 制箭台
            //#endif
            SmokerBlock.class,
            BlastFurnaceBlock.class,
            //#if MC >= 12003
            CrafterBlock.class,
            //#endif
            SignBlock.class,
    };

    public static boolean isInteractive(Block block) {
        //#if MC > 12004
        if (block == Blocks.VAULT) {
            return true;
        }
        //#endif
        for (Class<?> clazz : interactiveBlocks) {
            if (clazz.isInstance(block)) {
                return true;
            }
        }
        return false;
    }
}
