package me.aleksilassila.litematica.printer.integration.quickshulker;

final class ShulkerSelectionPolicy {
    private ShulkerSelectionPolicy() {
    }

    static int score(
            boolean recordedSlot,
            boolean sameShulkerType,
            boolean snapshotMatches,
            boolean originalSlotFits,
            boolean hasCapacity
    ) {
        if (snapshotMatches && originalSlotFits) return recordedSlot ? 0 : 1;
        if (snapshotMatches && hasCapacity) return recordedSlot ? 2 : 3;
        if (snapshotMatches) return recordedSlot ? 4 : 5;
        if (sameShulkerType && originalSlotFits) return recordedSlot ? 6 : 7;
        if (originalSlotFits) return recordedSlot ? 8 : 9;
        if (sameShulkerType && hasCapacity) return recordedSlot ? 10 : 11;
        if (hasCapacity) return recordedSlot ? 12 : 13;
        if (sameShulkerType) return recordedSlot ? 14 : 15;
        return recordedSlot ? 16 : 17;
    }
}
