package me.aleksilassila.litematica.printer.handler.handlers;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.integration.tweakeroo.TweakerooAdapter;
import me.aleksilassila.litematica.printer.mixin_extension.BlockBreakResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

final class MineToolSession {
    private Item sessionToolItem;
    private int remainingInstantBudget;
    private BlockPos lastSessionPos;
    private final TweakerooAdapter tweakeroo;

    MineToolSession(TweakerooAdapter tweakeroo) {
        this.tweakeroo = tweakeroo;
    }

    void reset() {
        this.sessionToolItem = null;
        this.remainingInstantBudget = 0;
        this.lastSessionPos = null;
    }

    void beginTick() {
        int configuredBudget = Configs.Break.BREAK_BLOCKS_PER_TICK.getIntegerValue();

        this.remainingInstantBudget = configuredBudget <= 0 ? -1 : configuredBudget;
    }

    Comparator<MineBreakExecutor.Target> comparator(LocalPlayer player) {
        BlockPos anchor = this.lastSessionPos;
        return Comparator
                .comparingDouble((MineBreakExecutor.Target target) -> localityScore(player, anchor, target))
                .thenComparingInt(target -> target.pos().getY())
                .thenComparingInt(target -> target.pos().getX())
                .thenComparingInt(target -> target.pos().getZ());
    }

    private static double localityScore(LocalPlayer player, BlockPos anchor, MineBreakExecutor.Target target) {
        if (anchor != null) {
            return target.pos().distSqr(anchor);
        }
        return distanceScore(player, target);
    }

    MineBreakExecutor.Target selectTarget(List<MineBreakExecutor.Target> candidates, MineBreakExecutor analyzer, LocalPlayer player) {
        MineBreakExecutor.Target nearest = candidates.get(0);
        if (this.lastSessionPos != null) {
            for (MineBreakExecutor.Target target : candidates) {
                if (target.pos().equals(this.lastSessionPos)) {
                    this.sessionToolItem = target.bestToolItem();
                    return target;
                }
            }
        }
        if (this.sessionToolItem != null) {
            for (MineBreakExecutor.Target target : candidates) {
                if (analyzer.hasSameBestTool(target, this.sessionToolItem)) {
                    this.lastSessionPos = target.pos();
                    return target;
                }
            }
        }
        this.sessionToolItem = nearest.bestToolItem();
        this.lastSessionPos = nearest.pos();
        return nearest;
    }

    void startSession(MineBreakExecutor.Target firstTarget) {
        this.sessionToolItem = firstTarget.bestToolItem();
    }

    boolean matchesSessionTool(MineBreakExecutor analyzer, MineBreakExecutor.Target target) {
        return analyzer.hasSameBestTool(target, this.sessionToolItem);
    }

    boolean shouldStop(BlockBreakResult result, boolean hasActiveMinePos) {
        return result == BlockBreakResult.IN_PROGRESS
                || result == BlockBreakResult.ABORTED
                || hasActiveMinePos
                || !this.hasInstantBudget()
                ;
    }

    void consumeInstantBudget() {
        if (this.remainingInstantBudget > 0) {
            this.remainingInstantBudget--;
        }
    }

    boolean hasInstantBudget() {
        return this.remainingInstantBudget < 0 || this.remainingInstantBudget > 0;
    }

    boolean ensureHandToolProtected(LocalPlayer player, MineBreakExecutor.Target target) {
        if (player == null || player.getAbilities().instabuild) {
            return true;
        }
        if (this.tweakeroo.isCurrentToolUsable(player.getMainHandItem())) {
            return true;
        }
        boolean protectedTool = this.tweakeroo.prepareCurrentTool(
                target == null ? null : target.pos(), target == null ? null : target.state());
        return protectedTool;
    }

    void onTargetResolved(BlockBreakResult result, BlockPos pos) {
        if ((result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT)
                && pos.equals(this.lastSessionPos)) {
            this.lastSessionPos = null;
        }
    }

    static double distanceScore(LocalPlayer player, MineBreakExecutor.Target target) {
        Vec3 eye = player.getEyePosition();
        return Vec3.atCenterOf(target.pos()).distanceToSqr(eye);
    }

}
