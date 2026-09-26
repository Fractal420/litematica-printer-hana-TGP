package me.aleksilassila.litematica.printer.handler.handlers.bedrock;

import java.util.HashMap;
import java.util.Map;

final class BedrockExposureGate<K> {
    private final int maxDeferrals;
    private final Map<K, Integer> deferrals = new HashMap<>();
    private final Map<K, Integer> bypassUses = new HashMap<>();

    BedrockExposureGate(int maxDeferrals) {
        this.maxDeferrals = Math.max(0, maxDeferrals);
    }

    Decision evaluate(K key, boolean shouldDefer, boolean mutate) {
        int bypass = this.bypassUses.getOrDefault(key, 0);
        if (bypass > 0) {
            if (mutate) {
                if (bypass == 1) {
                    this.bypassUses.remove(key);
                } else {
                    this.bypassUses.put(key, bypass - 1);
                }
            }
            return Decision.ALLOW;
        }

        if (shouldDefer) {
            int nextDeferral = this.deferrals.getOrDefault(key, 0) + 1;
            if (nextDeferral <= this.maxDeferrals) {
                if (mutate) {
                    this.deferrals.put(key, nextDeferral);
                }
                return Decision.DEFER;
            }
            if (mutate) {
                this.deferrals.remove(key);
                this.bypassUses.put(key, 1);
            }
            return Decision.ALLOW;
        }

        if (mutate) {
            this.deferrals.remove(key);
            this.bypassUses.remove(key);
        }
        return Decision.ALLOW;
    }

    void clear() {
        this.deferrals.clear();
        this.bypassUses.clear();
    }

    enum Decision {
        ALLOW,
        DEFER
    }
}
