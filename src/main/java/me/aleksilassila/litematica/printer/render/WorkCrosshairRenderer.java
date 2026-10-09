package me.aleksilassila.litematica.printer.render;

import me.aleksilassila.litematica.printer.handler.HudStatsManager;
import me.aleksilassila.litematica.printer.handler.handlers.MineHandler;
import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.InteractionUtils;
import me.aleksilassila.litematica.printer.utils.render.Render2DUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class WorkCrosshairRenderer {
    public static final WorkCrosshairRenderer INSTANCE = new WorkCrosshairRenderer();

    private static final float ICON_SCALE = 0.8F;
    private static final int ICON_SIZE = 16;

    private static final ItemStack IDLE_ICON = new ItemStack(Blocks.CAMPFIRE);
    private static final ItemStack PRINT_ICON = new ItemStack(Items.BRICKS);
    private static final ItemStack MINE_ICON = new ItemStack(Items.NETHERITE_PICKAXE);
    private static final ItemStack FLUID_ICON = new ItemStack(Items.WATER_BUCKET);
    private static final ItemStack FILL_ICON = new ItemStack(Items.SPONGE);
    private static final ItemStack COVER_ICON = new ItemStack(Blocks.OBSIDIAN);
    private static final ItemStack BEDROCK_ICON = new ItemStack(Blocks.BEDROCK);

    private WorkCrosshairRenderer() {
    }

    public void render(float scaledWidth, float scaledHeight) {
        if (!ConfigUtils.isEnable()) {
            return;
        }

        int cx = (int) (scaledWidth / 2);
        int cy = (int) (scaledHeight / 2);

        ItemStack icon = switch (resolveActiveMode()) {
            case PRINT -> PRINT_ICON;
            case MINE -> MINE_ICON;
            case FLUID -> FLUID_ICON;
            case FILL -> FILL_ICON;
            case COVER -> COVER_ICON;
            case BEDROCK -> BEDROCK_ICON;
            case IDLE -> IDLE_ICON;
        };

        int drawSize = Math.round(ICON_SIZE * ICON_SCALE);
        int x = cx - drawSize / 2;
        int y = cy - drawSize / 2 - 1;

        Render2DUtils.pushPose();
        Render2DUtils.translate(x, y, 0.0D);
        Render2DUtils.scale(ICON_SCALE, ICON_SCALE, 1.0F);
        Render2DUtils.drawItem(icon, 0, 0);
        Render2DUtils.popPose();
    }

    private enum ActiveMode {
        IDLE, PRINT, MINE, FLUID, FILL, COVER, BEDROCK
    }

    private ActiveMode resolveActiveMode() {
        if (isBreakingWorkActive()) {
            return ActiveMode.MINE;
        }

        HudStatsManager stats = HudStatsManager.getRuntime();
        if (ConfigUtils.isPrintMode() && isBusy(stats.snapshot(HudStatsManager.Mode.PRINT))) {
            return ActiveMode.PRINT;
        }
        if (ConfigUtils.isMineMode() && isBusy(stats.snapshot(HudStatsManager.Mode.MINE))) {
            return ActiveMode.MINE;
        }
        if (ConfigUtils.isFluidMode() && isBusy(stats.snapshot(HudStatsManager.Mode.FLUID))) {
            return ActiveMode.FLUID;
        }
        if (ConfigUtils.isFillMode() && isBusy(stats.snapshot(HudStatsManager.Mode.FILL))) {
            return ActiveMode.FILL;
        }
        if (ConfigUtils.isCoverMode() && isBusy(stats.snapshot(HudStatsManager.Mode.COVER))) {
            return ActiveMode.COVER;
        }
        if (ConfigUtils.isBedrockMode() && isBusy(stats.snapshot(HudStatsManager.Mode.BEDROCK))) {
            return ActiveMode.BEDROCK;
        }
        return ActiveMode.IDLE;
    }

    private static boolean isBreakingWorkActive() {
        MineHandler mine = RuntimeAccess.get().modules().mine();
        if (mine.hasActiveBreakingWork()) {
            return true;
        }
        InteractionUtils interaction = InteractionUtils.getRuntime();
        return interaction.hasActiveDestroyTarget() || interaction.isNeedHandle();
    }

    private static boolean isBusy(HudStatsManager.Snapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return snapshot.completedRatePerSecond() > 0.0D || snapshot.ratePerSecond() > 0.0D;
    }
}
