package me.aleksilassila.litematica.printer.printer;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;

public final class PlacementRateController implements RuntimeComponent {
    private final RttReplayController rttReplayController;
    private long lastSentTick = Long.MIN_VALUE;
    private long windowStartTick = Long.MIN_VALUE;
    private int sentInWindow;

    public PlacementRateController(RttReplayController rttReplayController) {
        this.rttReplayController = rttReplayController;
    }

    public int effectiveIntervalTicks() {
        int baseInterval = Math.max(0, Configs.Placement.PLACE_INTERVAL.getIntegerValue());
        if (!Configs.Placement.RTT_ADAPTIVE_INTERVAL.getBooleanValue()) {
            return baseInterval;
        }
        return Math.max(baseInterval, this.rttReplayController.getExtraIntervalTicks(
                Configs.Placement.RTT_SAFETY_PERCENT.getIntegerValue()));
    }

    public int maxSendsPerWindow() {
        return Math.max(1, Configs.Placement.PLACE_BLOCKS_PER_TICK.getIntegerValue());
    }

    public boolean canSend(long currentTick) {
        this.rollWindow(currentTick);
        return this.sentInWindow < this.maxSendsPerWindow();
    }

    public void recordSent(long currentTick) {
        this.rollWindow(currentTick);
        this.sentInWindow++;
        this.lastSentTick = currentTick;
    }

    private void rollWindow(long currentTick) {
        int interval = this.effectiveIntervalTicks();
        if (this.windowStartTick == Long.MIN_VALUE) {
            this.windowStartTick = currentTick;
            this.sentInWindow = 0;
            return;
        }
        if (interval <= 0) {
            if (this.windowStartTick != currentTick) {
                this.windowStartTick = currentTick;
                this.sentInWindow = 0;
            }
            return;
        }
        if (currentTick - this.windowStartTick >= interval) {
            this.windowStartTick = currentTick;
            this.sentInWindow = 0;
        }
    }

    public long lastSentTick() {
        return this.lastSentTick;
    }

    public boolean isAdaptiveActive() {
        return Configs.Placement.RTT_ADAPTIVE_INTERVAL.getBooleanValue()
                && this.rttReplayController.getEstimatedRttMillis() > 0;
    }

    public void reset() {
        this.lastSentTick = Long.MIN_VALUE;
        this.windowStartTick = Long.MIN_VALUE;
        this.sentInWindow = 0;
    }

    @Override
    public void onEpochChanged(RuntimeEvent.EpochChanged event) {
        this.reset();
    }
}
