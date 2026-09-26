package me.aleksilassila.litematica.printer.printer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;
import me.aleksilassila.litematica.printer.runtime.PrinterRuntime;

public final class RttReplayController implements RuntimeComponent {
    private static final int MILLIS_PER_TICK = 50;

    private static final double SMOOTHING = 0.25D;

    private static final int MAX_EXTRA_TICKS = 40;

    private double smoothedRttMillis = 0.0D;
    private long sampledTick = Long.MIN_VALUE;
    private double sampledRttMillis = 0.0D;

    public RttReplayController() {
    }

    public int getExtraIntervalTicks(int safetyPercent) {
        double rttMillis = this.sampleRttMillis();
        if (rttMillis <= 0.0D) {
            return 0;
        }
        return intervalTicksFor(rttMillis, safetyPercent);
    }

    public int getWaitTimeoutTicks(int baseTicks, int safetyTicks, int maxTicks) {
        return waitTimeoutTicksFor(
                this.getExtraIntervalTicks(100),
                baseTicks,
                safetyTicks,
                maxTicks
        );
    }

    static int waitTimeoutTicksFor(int rttTicks, int baseTicks, int safetyTicks, int maxTicks) {
        int base = Math.max(0, baseTicks);
        int safety = Math.max(0, safetyTicks);
        int cap = Math.max(base, maxTicks);
        long candidate = (long) base + Math.max(0, rttTicks) + safety;
        return (int) Math.min(cap, candidate);
    }

    static int intervalTicksFor(double rttMillis, int safetyPercent) {
        double effectiveMillis = rttMillis * Math.max(0, safetyPercent) / 100.0D;
        int ticks = (int) Math.ceil(effectiveMillis / MILLIS_PER_TICK);
        return Math.max(0, Math.min(MAX_EXTRA_TICKS, ticks));
    }

    public int getEstimatedRttMillis() {
        return (int) Math.round(this.smoothedRttMillis);
    }

    public void reset() {
        this.smoothedRttMillis = 0.0D;
        this.sampledTick = Long.MIN_VALUE;
        this.sampledRttMillis = 0.0D;
    }

    @Override public void onEpochChanged(RuntimeEvent.EpochChanged event) { this.reset(); }

    private double sampleRttMillis() {
        long currentTick = currentGameTick();
        if (currentTick != Long.MIN_VALUE && currentTick == this.sampledTick) {
            return this.sampledRttMillis;
        }
        int rawLatency = readLatencyMillis();
        if (rawLatency <= 0) {

            this.sampledRttMillis = this.smoothedRttMillis > 0.0D ? this.smoothedRttMillis : 0.0D;
            this.sampledTick = currentTick;
            return this.sampledRttMillis;
        }
        if (this.smoothedRttMillis <= 0.0D) {
            this.smoothedRttMillis = rawLatency;
        } else {
            this.smoothedRttMillis = this.smoothedRttMillis * (1.0D - SMOOTHING) + rawLatency * SMOOTHING;
        }
        this.sampledRttMillis = this.smoothedRttMillis;
        this.sampledTick = currentTick;
        return this.sampledRttMillis;
    }

    private static long currentGameTick() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
    }

    private static int readLatencyMillis() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientPacketListener connection = client.getConnection();
        if (player == null || connection == null) {
            return 0;
        }
        PlayerInfo info = connection.getPlayerInfo(player.getUUID());
        if (info == null) {
            return 0;
        }
        return Math.max(0, info.getLatency());
    }
}
