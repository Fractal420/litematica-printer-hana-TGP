package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.runtime.RuntimeAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.LongSupplier;
import java.util.function.Predicate;

public final class InventorySwitchGuard {

    private static final int MAX_SETTLE_TICKS = 16;
    private final Minecraft client;
    private final LongSupplier tickClock;
    private Item pendingItem;
    private int pendingDamage = -1;
    private boolean matchDamage;
    private int pendingSlot = -1;
    private int lastSyncedSlot = -1;
    private long lastSyncTick = Long.MIN_VALUE;
    private final SwitchConfirmationWindow confirmationWindow = new SwitchConfirmationWindow(MAX_SETTLE_TICKS);

    public InventorySwitchGuard(Minecraft client, LongSupplier tickClock) {
        this.client = client;
        this.tickClock = tickClock;
    }

    public void reset() {
        clear();
        this.lastSyncedSlot = -1;
        this.lastSyncTick = Long.MIN_VALUE;
    }

    public boolean markSwitchIfNeeded(Item item) {
        if (item == null) {
            return false;
        }
        int slot = currentSelectedSlot();
        if (pendingItem == item && !matchDamage && pendingSlot == slot && confirmationWindow.isActive()) {
            return true;
        }
        pendingItem = item;
        pendingDamage = -1;
        matchDamage = false;
        pendingSlot = slot;
        noteSyncedSlot(slot);
        this.confirmationWindow.begin(this.tickClock.getAsLong(), adaptiveMinSettleTicks());
        return true;
    }

    public boolean markSwitchIfNeeded(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        boolean checkDamage = stack.isDamageableItem();
        int damage = checkDamage ? stack.getDamageValue() : -1;
        int slot = currentSelectedSlot();
        if (pendingItem == item && matchDamage == checkDamage
                && pendingDamage == damage && pendingSlot == slot && confirmationWindow.isActive()) {
            return true;
        }
        pendingItem = item;
        matchDamage = checkDamage;
        pendingDamage = damage;
        pendingSlot = slot;
        noteSyncedSlot(slot);
        this.confirmationWindow.begin(this.tickClock.getAsLong(), adaptiveMinSettleTicks());
        return true;
    }

    public void noteSyncedSlot(int slot) {
        if (this.lastSyncedSlot < 0) {
            this.lastSyncedSlot = slot;
            this.lastSyncTick = Long.MIN_VALUE;
            return;
        }
        if (slot != this.lastSyncedSlot) {
            this.lastSyncedSlot = slot;
            this.lastSyncTick = this.tickClock.getAsLong();
        }
    }

    public boolean isWaiting() {
        if (pendingItem != null && this.confirmationWindow.isWaiting(this.tickClock.getAsLong(), this.isMainHandReady())) {
            return true;
        }
        return !isSelectedSlotSettled();
    }

    public boolean isSelectedSlotSettled() {
        if (client.player == null) {
            return false;
        }
        int slot = currentSelectedSlot();
        long tick = this.tickClock.getAsLong();
        if (this.lastSyncedSlot < 0) {
            return true;
        }
        if (slot != this.lastSyncedSlot) {
            return false;
        }
        return tick > this.lastSyncTick;
    }

    public boolean isReadyToPlace(Item[] expectedItems, Predicate<ItemStack> expectedStackPredicate) {
        if (client.player == null) {
            return false;
        }
        if (isWaiting()) {
            return false;
        }
        if (!isSelectedSlotSettled()) {
            return false;
        }
        ItemStack hand = client.player.getMainHandItem();
        if (hand.isEmpty()) {
            return expectedAllowsEmpty(expectedItems, expectedStackPredicate);
        }
        if (expectedStackPredicate != null) {
            return expectedStackPredicate.test(hand);
        }
        if (expectedItems == null || expectedItems.length == 0) {
            return false;
        }
        Item held = hand.getItem();
        for (Item expected : expectedItems) {
            if (expected != null && held == expected) {
                return true;
            }
        }
        return false;
    }

    public Item getPendingItem() {
        return pendingItem;
    }

    public boolean isPendingMatching(Item[] items) {
        if (pendingItem == null || items == null || items.length == 0) {
            return false;
        }
        for (Item item : items) {
            if (item != null && item == pendingItem) {
                return true;
            }
        }
        return false;
    }

    public boolean isPendingMatching(Predicate<ItemStack> predicate) {
        if (pendingItem == null || predicate == null) {
            return false;
        }
        if (client.player != null) {
            ItemStack hand = client.player.getMainHandItem();
            if (hand.is(pendingItem) && predicate.test(hand)) {
                return true;
            }
        }
        return predicate.test(new ItemStack(pendingItem));
    }

    public void clearIfPending(Item item) {
        if (item != null && pendingItem == item) {
            clearPendingOnly();
        }
    }

    private void clearPendingOnly() {
        pendingItem = null;
        pendingDamage = -1;
        matchDamage = false;
        pendingSlot = -1;
        this.confirmationWindow.clear();
    }

    private void clear() {
        clearPendingOnly();
    }

    private int adaptiveMinSettleTicks() {
        int rttMs = 0;
        try {
            rttMs = RuntimeAccess.get().rttReplayController().getEstimatedRttMillis();
        } catch (Throwable ignored) {
        }
        if (rttMs <= 0) {
            return 3;
        }
        int ticks = (int) Math.ceil(rttMs / 50.0D);
        return Math.max(3, Math.min(MAX_SETTLE_TICKS, ticks));
    }

    private int currentSelectedSlot() {
        if (client.player == null) {
            return -1;
        }
        return InventoryUtils.getSelectedSlot(client.player.getInventory());
    }

    private boolean isMainHandReady() {
        return isMainHandReadyFor(pendingItem, pendingDamage, matchDamage);
    }

    private boolean isMainHandReadyFor(Item item, int damage, boolean checkDamage) {
        if (client.player == null || item == null) {
            return false;
        }
        ItemStack hand = client.player.getMainHandItem();
        if (pendingSlot >= 0 && currentSelectedSlot() != pendingSlot) {
            return false;
        }
        return hand.is(item) && (!checkDamage || hand.getDamageValue() == damage);
    }

    private static boolean expectedAllowsEmpty(Item[] expectedItems, Predicate<ItemStack> expectedStackPredicate) {
        if (expectedStackPredicate != null) {
            return expectedStackPredicate.test(ItemStack.EMPTY);
        }
        if (expectedItems == null || expectedItems.length == 0) {
            return false;
        }
        for (Item expected : expectedItems) {
            if (expected == null || expected == net.minecraft.world.item.Items.AIR) {
                return true;
            }
        }
        return false;
    }
}
