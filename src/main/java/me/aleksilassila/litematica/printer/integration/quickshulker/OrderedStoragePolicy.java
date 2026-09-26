package me.aleksilassila.litematica.printer.integration.quickshulker;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Set;

final class OrderedStoragePolicy {
    private OrderedStoragePolicy() {
    }

    static int countEmptyInventorySlots(LocalPlayer player) {
        int emptySlots = 0;
        int size = Math.min(36, player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (player.getInventory().getItem(slot).isEmpty()) emptySlots++;
        }
        return emptySlots;
    }

    static void normalizeActivityTicks(
            RestoreSession<OrderedStorageEntry> session,
            List<OrderedStorageEntry> entries,
            long currentTick
    ) {
        session.normalizeActivity(currentTick);
        for (OrderedStorageEntry entry : entries) {
            if (currentTick < entry.lastUseTick) entry.lastUseTick = currentTick;
        }
    }

    static OrderedStorageEntry selectRestoreCandidate(
            LocalPlayer player,
            List<OrderedStorageEntry> entries,
            Set<Item> requestedItems,
            long currentTick,
            int recentUseProtectionTicks,
            boolean allowRecentlyUsed,
            boolean allowCurrentMainHand
    ) {
        int selectedSlot = me.aleksilassila.litematica.printer.utils.InventoryUtils
                .getSelectedSlot(player.getInventory());
        OrderedStorageEntry selected = null;
        for (OrderedStorageEntry entry : entries) {
            if (!OrderedStorageTracking.isValid(player, entry)
                    || !allowCurrentMainHand && entry.playerInventorySlot == selectedSlot
                    && OrderedStorageStacks.matches(entry.itemStack, player.getMainHandItem())
                    || requestedItems.contains(entry.itemStack.getItem())) {
                continue;
            }
            long age = currentTick - entry.lastUseTick;
            if (!allowRecentlyUsed && age < recentUseProtectionTicks) continue;
            if (selected == null
                    || entry.lastUseTick < selected.lastUseTick
                    || entry.lastUseTick == selected.lastUseTick
                    && entry.playerInventorySlot < selected.playerInventorySlot) {
                selected = entry;
            }
        }
        return selected;
    }
}
