package me.aleksilassila.litematica.printer.integration.quickshulker;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class OrderedStorageTracking {
    private OrderedStorageTracking() {
    }

    static void reconcile(List<OrderedStorageEntry> entries, LocalPlayer player) {
        Set<Integer> claimedSlots = new HashSet<>();
        List<OrderedStorageEntry> unresolved = new ArrayList<>();
        for (OrderedStorageEntry entry : entries) {
            if (isValid(player, entry) && claimedSlots.add(entry.playerInventorySlot)) {
                continue;
            }
            unresolved.add(entry);
        }
        for (OrderedStorageEntry entry : unresolved) {
            int relocatedSlot = findMatchingInventorySlot(player, entry.itemStack, claimedSlots);
            if (relocatedSlot >= 0) {
                entry.playerInventorySlot = relocatedSlot;
                claimedSlots.add(relocatedSlot);
            } else {
                entries.remove(entry);
            }
        }
    }

    static OrderedStorageEntry findAtSlot(List<OrderedStorageEntry> entries, int playerSlot, ItemStack stack) {
        for (OrderedStorageEntry entry : entries) {
            if (entry.playerInventorySlot == playerSlot
                    && OrderedStorageStacks.matches(entry.itemStack, stack)) {
                return entry;
            }
        }
        return null;
    }

    static boolean isValid(LocalPlayer player, OrderedStorageEntry entry) {
        return entry.playerInventorySlot >= 0
                && entry.playerInventorySlot < player.getInventory().getContainerSize()
                && OrderedStorageStacks.matches(
                        entry.itemStack,
                        player.getInventory().getItem(entry.playerInventorySlot)
                );
    }

    private static int findMatchingInventorySlot(
            LocalPlayer player,
            ItemStack expected,
            Set<Integer> claimedSlots
    ) {
        int selectedSlot = me.aleksilassila.litematica.printer.utils.InventoryUtils
                .getSelectedSlot(player.getInventory());
        if (!claimedSlots.contains(selectedSlot)
                && OrderedStorageStacks.matches(expected, player.getInventory().getItem(selectedSlot))) {
            return selectedSlot;
        }
        int size = Math.min(36, player.getInventory().getContainerSize());
        for (int slot = 0; slot < size; slot++) {
            if (!claimedSlots.contains(slot)
                    && OrderedStorageStacks.matches(expected, player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }
}
