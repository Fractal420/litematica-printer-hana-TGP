package me.aleksilassila.litematica.printer.guide;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.guides.*;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class Guides {
    private final List<GuideRegistration> registrations = new ArrayList<>();
    private final Map<Class<?>, List<GuideRegistration>> registrationsByBlockClass = new IdentityHashMap<>();

    public Guides() {

        register(WaterGuide::new);

        register(SkipGuide::new,
                LiquidBlock.class,
                BubbleColumnBlock.class,
                LilyPadBlock.class
        );

        register(TorchGuide::new,
                //#if MC > 12002
                BaseTorchBlock.class
                //#else
                //$$ TorchBlock.class
                //#endif
        );

        register(AmethystGuide::new, AmethystClusterBlock.class);

        register(FlowerGuide::new, FlowerBlock.class);

        register(SlabGuide::new, SlabBlock.class);

        register(StairGuide::new, StairBlock.class);

        register(TrapDoorGuide::new, TrapDoorBlock.class);

        register(DoorGuide::new, DoorBlock.class);

        register(FenceGateGuide::new, FenceGateBlock.class);

        register(BedGuide::new, BedBlock.class);

        register(BellGuide::new, BellBlock.class);

        register(ObserverGuide::new, ObserverBlock.class);

        register(PistonGuide::new, PistonBaseBlock.class);

        register(ChestGuide::new, ChestBlock.class, TrappedChestBlock.class);

        register(SignGuide::new,
                StandingSignBlock.class,
                WallSignBlock.class
                //#if MC >= 12002
                , WallHangingSignBlock.class
                , CeilingHangingSignBlock.class
                //#endif
        );

        register(BannerGuide::new, AbstractBannerBlock.class);

        register(SkullGuide::new, SkullBlock.class, WallSkullBlock.class);

        register(NetherPortalGuide::new, NetherPortalBlock.class);

        register(LadderGuide::new, LadderBlock.class);

        register(LanternGuide::new, LanternBlock.class);

        register(RodGuide::new, RodBlock.class);

        register(HopperGuide::new, HopperBlock.class);

        register(AnvilGuide::new, AnvilBlock.class);

        register(StripLogGuide::new, RotatedPillarBlock.class);

        register(CocoaGuide::new, CocoaBlock.class);

        register(TripWireHookGuide::new, TripWireHookBlock.class);

        register(RailGuide::new, BaseRailBlock.class);

        //#if MC >= 12003
        register(CrafterGuide::new, CrafterBlock.class);
        //#endif

        register(CandleGuide::new, CandleBlock.class);

        register(SeaPickleGuide::new, SeaPickleBlock.class);

        register(TurtleEggGuide::new, TurtleEggBlock.class);

        register(RepeaterGuide::new, RepeaterBlock.class);

        register(ComparatorGuide::new, net.minecraft.world.level.block.ComparatorBlock.class);

        register(RedstoneWireGuide::new, RedStoneWireBlock.class);

        register(LeverGuide::new, LeverBlock.class);

        register(CampfireGuide::new, CampfireBlock.class);

        register(CropsGuide::new,
                AttachedStemBlock.class, StemBlock.class, CropBlock.class, BeetrootBlock.class);

        register(NoteBlockGuide::new, NoteBlock.class);

        register(SnowGuide::new, SnowLayerBlock.class);

        register(EndPortalFrameGuide::new, EndPortalFrameBlock.class);

        register(DaylightDetectorGuide::new, DaylightDetectorBlock.class);

        //#if MC >= 11904
        register(FlowerBedGuide::new,
                //#if MC >= 12105
                FlowerBedBlock.class
                //#else
                //$$ PinkPetalsBlock.class
                //#endif
        );
        //#endif

        register(VineGuide::new, VineBlock.class, GlowLichenBlock.class);

        register(FireGuide::new, FireBlock.class, SoulFireBlock.class);

        register(CauldronGuide::new,
                CauldronBlock.class, LavaCauldronBlock.class, LayeredCauldronBlock.class);

        register(ComposterGuide::new, ComposterBlock.class);

        register(SoilGuide::new, FarmlandBlock.class, DirtPathBlock.class);

        register(FlowerPotGuide::new, FlowerPotBlock.class);

        register(ClimbingPlantGuide::new,
                BigDripleafStemBlock.class,
                CaveVinesBlock.class, CaveVinesPlantBlock.class,
                WeepingVinesBlock.class, WeepingVinesPlantBlock.class,
                TwistingVinesBlock.class, TwistingVinesPlantBlock.class);

        register(CoralGuide::new);

        register(DefaultGuide::new);
    }

    @SafeVarargs
    public final void register(
            Function<SchematicBlockContext, ? extends Guide> factory,
            Class<? extends Block>... supportedBlocks
    ) {
        this.registrations.add(new GuideRegistration(factory, supportedBlocks));
        this.registrationsByBlockClass.clear();
    }

    public final Optional<Action> buildAction(SchematicBlockContext context) {
        BlockMatchResult blockMatchResult = BlockMatchResult.compare(context);
        Block requiredBlock = context.requiredState.getBlock();
        List<GuideRegistration> matching = this.registrationsByBlockClass.computeIfAbsent(
                requiredBlock.getClass(),
                ignored -> this.registrations.stream()
                        .filter(registration -> registration.matches(requiredBlock))
                        .toList()
        );
        for (GuideRegistration registration : matching) {
            Guide guide = registration.create(context);
            if (!guide.canExecute()) {
                continue;
            }
            Result result = guide.buildAction(blockMatchResult);
            if (result.hasAction()) {
                return result.toOptional();
            }
            if (result.skipOtherGuide()) {
                break;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("ClassCanBeRecord")
    private static class GuideRegistration {
        private final Function<SchematicBlockContext, ? extends Guide> factory;
        public final Class<? extends Block>[] blockClass;

        public GuideRegistration(
                Function<SchematicBlockContext, ? extends Guide> factory,
                Class<? extends Block>[] blockClass
        ) {
            this.factory = factory;
            this.blockClass = blockClass;
        }

        public Guide create(SchematicBlockContext context) {
            return this.factory.apply(context);
        }

        public boolean matches(Block block) {
            if (this.blockClass.length == 0) {
                return true;
            }
            for (Class<? extends Block> clazz : this.blockClass) {
                if (clazz.isInstance(block)) {
                    return true;
                }
            }
            return false;
        }

    }
}
