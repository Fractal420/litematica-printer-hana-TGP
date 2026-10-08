package me.aleksilassila.litematica.printer.handler.handlers.print;

import fi.dy.masa.litematica.world.WorldSchematic;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import me.aleksilassila.litematica.printer.handler.scan.ScanEngine;
import me.aleksilassila.litematica.printer.handler.scan.ScanIntent;
import me.aleksilassila.litematica.printer.handler.scan.ScanAvailability;
import me.aleksilassila.litematica.printer.handler.scan.ScanCandidateIterable;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

public final class SortedSchematicTargetQueue implements ScanCandidateIterable {
    private static final int MAX_SORT_BUFFER = 4096;
    private final ScanEngine scanEngine;
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final LongSet queuedKeys = new LongOpenHashSet();
    private List<PrinterBox> boxes = List.of();
    private boolean hasMoreSource;
    private long lastFillTick = Long.MIN_VALUE;
    private long lastDirtyVersion = Long.MIN_VALUE;
    private BlockPos lastServedPos;
    private Item preferredItem;

    public SortedSchematicTargetQueue(ScanEngine scanEngine) {
        this.scanEngine = scanEngine;
    }

    public void clear() {
        this.queue.clear();
        this.queuedKeys.clear();
        this.boxes = List.of();
        this.hasMoreSource = false;
        this.lastFillTick = Long.MIN_VALUE;
        this.lastDirtyVersion = Long.MIN_VALUE;
        this.lastServedPos = null;
        this.preferredItem = null;
    }

    public void setPreferredItem(Item item) {
        if (item == null || item == Items.AIR) {
            this.preferredItem = null;
        } else {
            this.preferredItem = item;
        }
    }

    public Item getPreferredItem() {
        return this.preferredItem;
    }

    public Iterable<BlockPos> iterable(List<PrinterBox> sourceBoxes, ClientLevel level, WorldSchematic schematic, LocalPlayer player, int scanGuardLimit) {
        if (!this.boxes.equals(sourceBoxes)) {
            this.queue.clear();
            this.queuedKeys.clear();
            this.hasMoreSource = true;
            this.lastFillTick = Long.MIN_VALUE;
        }
        this.boxes = List.copyOf(sourceBoxes);
        this.fill(sourceBoxes, level, schematic, player, scanGuardLimit);
        return this;
    }

    public boolean hasPendingWork() {
        return !this.queue.isEmpty() || this.hasMoreSource;
    }

    @Override
    public ScanAvailability availability() {
        if (!this.queue.isEmpty()) {
            return ScanAvailability.READY;
        }
        return this.hasMoreSource ? ScanAvailability.PAUSED : ScanAvailability.COMPLETE;
    }

    @Override
    public boolean isBuffered() {
        return true;
    }

    public void requeue(BlockPos pos) {
        if (pos == null || this.queuedKeys.add(ScanEngine.key(pos))) {
            if (pos != null) {
                this.queue.addLast(pos.immutable());
            }
        }
    }

    public void remove(BlockPos pos) {
        if (pos == null) return;
        long key = ScanEngine.key(pos);
        this.queuedKeys.remove(key);
        this.queue.removeIf(candidate -> ScanEngine.key(candidate) == key);
    }

    private void fill(List<PrinterBox> sourceBoxes, ClientLevel level, WorldSchematic schematic, LocalPlayer player, int scanGuardLimit) {
        long currentTick = level.getGameTime();
        long dirtyVersion = this.scanEngine.dirtyVersion();
        boolean dirtyChanged = dirtyVersion != this.lastDirtyVersion;
        int configuredThroughput = Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue();
        int targetBufferSize = configuredThroughput > 0
                ? Math.min(MAX_SORT_BUFFER, Math.max(256, configuredThroughput * 16))
                : MAX_SORT_BUFFER;
        int lowWater = Math.max(configuredThroughput * 4, 16);
        if (!shouldRefill(
                currentTick,
                this.lastFillTick,
                dirtyChanged,
                this.hasMoreSource,
                this.queue.size(),
                targetBufferSize,
                lowWater
        )) {
            return;
        }
        this.lastFillTick = currentTick;
        this.lastDirtyVersion = dirtyVersion;
        int remainingBuffer = Math.max(1, targetBufferSize - this.queue.size());
        int collectLimit = scanGuardLimit > 0
                ? Math.min(scanGuardLimit, remainingBuffer)
                : remainingBuffer;
        Item heldItem = player.getMainHandItem().getItem();
        if (this.preferredItem == null && heldItem instanceof BlockItem) {
            this.preferredItem = heldItem;
        }
        Item affinityItem = this.preferredItem;
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getLookAngle().normalize();
        List<TargetScore> preferredTargets = new ArrayList<>();
        List<TargetScore> otherTargets = new ArrayList<>();
        this.hasMoreSource = false;
        Iterable<BlockPos> candidates = this.scanEngine.iterable(
                "print_sorted",
                sourceBoxes,
                level,
                schematic,
                player,
                scanGuardLimit,
                ScanIntent.PRINT,
                pos -> true
        );
        for (BlockPos candidate : candidates) {
            long key = ScanEngine.key(candidate);
            if (this.queuedKeys.contains(key)) {
                continue;
            }
            TargetScore score = scoreTarget(schematic, heldItem, affinityItem, eye, view, candidate, this.lastServedPos);
            if (affinityItem != null && score.preferredMismatch) {
                if (otherTargets.size() < collectLimit) {
                    otherTargets.add(score);
                }
                this.hasMoreSource = true;
                continue;
            }
            if (preferredTargets.size() >= collectLimit) {
                this.hasMoreSource = true;
                break;
            }
            if (this.queuedKeys.add(key)) {
                preferredTargets.add(score);
            }
        }
        if (candidates instanceof ScanCandidateIterable scanSource
                && scanSource.availability() == ScanAvailability.PAUSED) {
            this.hasMoreSource = true;
        }
        List<TargetScore> accepted;
        if (!preferredTargets.isEmpty()) {
            accepted = preferredTargets;
            if (!otherTargets.isEmpty()) {
                this.hasMoreSource = true;
            }
        } else if (!otherTargets.isEmpty()) {
            Item nextPreferred = null;
            for (TargetScore score : otherTargets) {
                Item item = schematic.getBlockState(score.pos()).getBlock().asItem();
                if (item != null && item != Items.AIR) {
                    nextPreferred = item;
                    break;
                }
            }
            this.preferredItem = nextPreferred;
            for (int i = 0; i < otherTargets.size(); i++) {
                TargetScore old = otherTargets.get(i);
                Item item = schematic.getBlockState(old.pos()).getBlock().asItem();
                boolean preferredMismatch = nextPreferred != null && item != nextPreferred;
                otherTargets.set(i, new TargetScore(
                        old.pos(),
                        old.heldItemMismatch,
                        preferredMismatch,
                        old.fallingBlock,
                        old.y(),
                        old.distanceSqr(),
                        old.localitySqr(),
                        old.viewAngleScore()
                ));
            }
            if (nextPreferred != null) {
                accepted = new ArrayList<>();
                for (TargetScore score : otherTargets) {
                    if (!score.preferredMismatch) {
                        accepted.add(score);
                    } else {
                        this.hasMoreSource = true;
                    }
                }
                if (accepted.isEmpty()) {
                    accepted = otherTargets;
                }
            } else {
                accepted = otherTargets;
            }
        } else {
            accepted = List.of();
        }
        for (TargetScore score : accepted) {
            this.queuedKeys.add(ScanEngine.key(score.pos()));
        }
        accepted.sort(TargetScore.COMPARATOR);
        for (TargetScore target : accepted) {
            this.queue.addLast(target.pos());
        }
    }

    static boolean shouldRefill(
            long currentTick,
            long lastFillTick,
            boolean dirtyChanged,
            boolean hasMoreSource,
            int queueSize,
            int targetBufferSize,
            int lowWater
    ) {
        if (lastFillTick == currentTick || queueSize >= targetBufferSize) {
            return false;
        }
        if (!dirtyChanged && !hasMoreSource) {
            return false;
        }
        return dirtyChanged || queueSize < lowWater;
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return new Iterator<>() {

            private int remaining = queue.size();

            @Override
            public boolean hasNext() {
                return this.remaining > 0 && !queue.isEmpty();
            }

            @Override
            public BlockPos next() {
                if (this.remaining > 0 && !queue.isEmpty()) {
                    this.remaining--;
                    BlockPos result = queue.removeFirst();
                    queuedKeys.remove(ScanEngine.key(result));
                    lastServedPos = result;
                    return result;
                }
                throw new java.util.NoSuchElementException("sorted schematic target queue is exhausted");
            }
        };
    }

    private static TargetScore scoreTarget(
            WorldSchematic schematic,
            Item heldItem,
            Item preferredItem,
            Vec3 eye,
            Vec3 view,
            BlockPos pos,
            BlockPos anchor
    ) {
        double dx = pos.getX() + 0.5D - eye.x;
        double dy = pos.getY() + 0.5D - eye.y;
        double dz = pos.getZ() + 0.5D - eye.z;
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        double localitySqr = anchor == null
                ? distanceSqr
                : pos.distSqr(anchor);
        double viewAngleScore = distanceSqr < 1.0E-6D
                ? 0.0D
                : -(view.x * dx + view.y * dy + view.z * dz) / Math.sqrt(distanceSqr);
        BlockState requiredState = schematic.getBlockState(pos);
        Item requiredItem = requiredState.getBlock().asItem();
        boolean preferredMismatch = preferredItem != null
                && preferredItem != Items.AIR
                && requiredItem != preferredItem;
        boolean heldMismatch = heldItem != null
                && heldItem != Items.AIR
                && requiredItem != heldItem;
        return new TargetScore(
                pos,
                heldMismatch,
                preferredMismatch,
                requiredState.getBlock() instanceof FallingBlock,
                pos.getY(),
                distanceSqr,
                localitySqr,
                viewAngleScore
        );
    }

    static record TargetScore(
            BlockPos pos,
            boolean heldItemMismatch,
            boolean preferredMismatch,
            boolean fallingBlock,
            int y,
            double distanceSqr,
            double localitySqr,
            double viewAngleScore
    ) {

        static final Comparator<TargetScore> COMPARATOR = Comparator
                .comparing(TargetScore::fallingBlock)
                .thenComparingInt(score -> score.fallingBlock ? score.y : 0)
                .thenComparing(TargetScore::preferredMismatch)
                .thenComparing(TargetScore::heldItemMismatch)
                .thenComparingDouble(TargetScore::distanceSqr)
                .thenComparingDouble(TargetScore::viewAngleScore)
                .thenComparingDouble(TargetScore::localitySqr)
                .thenComparingInt(score -> score.pos.getY())
                .thenComparingInt(score -> score.pos.getX())
                .thenComparingInt(score -> score.pos.getZ());
    }
}
