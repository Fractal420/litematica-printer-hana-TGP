package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;

import java.util.ArrayList;
import java.util.List;

public final class BoxRegionDiff {
    private BoxRegionDiff() {
    }

    public static Result newlyExposed(PrinterBox previous, PrinterBox current) {
        int overlapMinX = Math.max(previous.minX, current.minX);
        int overlapMinY = Math.max(previous.minY, current.minY);
        int overlapMinZ = Math.max(previous.minZ, current.minZ);
        int overlapMaxX = Math.min(previous.maxX, current.maxX);
        int overlapMaxY = Math.min(previous.maxY, current.maxY);
        int overlapMaxZ = Math.min(previous.maxZ, current.maxZ);
        if (overlapMinX > overlapMaxX || overlapMinY > overlapMaxY || overlapMinZ > overlapMaxZ) {
            return Result.fullScan();
        }

        List<PrinterBox> boxes = new ArrayList<>(6);
        add(boxes, current.minX, current.minY, current.minZ,
                overlapMinX - 1, current.maxY, current.maxZ);
        add(boxes, overlapMaxX + 1, current.minY, current.minZ,
                current.maxX, current.maxY, current.maxZ);
        add(boxes, overlapMinX, current.minY, current.minZ,
                overlapMaxX, overlapMinY - 1, current.maxZ);
        add(boxes, overlapMinX, overlapMaxY + 1, current.minZ,
                overlapMaxX, current.maxY, current.maxZ);
        add(boxes, overlapMinX, overlapMinY, current.minZ,
                overlapMaxX, overlapMaxY, overlapMinZ - 1);
        add(boxes, overlapMinX, overlapMinY, overlapMaxZ + 1,
                overlapMaxX, overlapMaxY, current.maxZ);
        return new Result(false, List.copyOf(boxes));
    }

    private static void add(
            List<PrinterBox> boxes,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        if (minX <= maxX && minY <= maxY && minZ <= maxZ) {
            boxes.add(new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ));
        }
    }

    public record Result(boolean requiresFullScan, List<PrinterBox> boxes) {
        private static Result fullScan() {
            return new Result(true, List.of());
        }
    }
}
