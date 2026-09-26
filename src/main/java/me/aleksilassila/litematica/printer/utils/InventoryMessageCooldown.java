package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.core.runtime.RuntimeComponent;
import me.aleksilassila.litematica.printer.core.runtime.RuntimeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InventoryMessageCooldown implements RuntimeComponent {
    private static final long MESSAGE_COOLDOWN_MS = 5000L;

    private final Map<String, Long> lastMessageSendTime = new ConcurrentHashMap<>();

    public boolean shouldSend(String messageKey, long now) {
        Long last = this.lastMessageSendTime.get(messageKey);
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) {
            return false;
        }
        this.lastMessageSendTime.put(messageKey, now);
        return true;
    }

    public void reset() {
        this.lastMessageSendTime.clear();
    }

    @Override
    public void onEpochChanged(RuntimeEvent.EpochChanged event) {
        this.reset();
    }
}
