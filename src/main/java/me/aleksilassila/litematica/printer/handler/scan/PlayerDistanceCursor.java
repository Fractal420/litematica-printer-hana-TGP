package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.PriorityQueue;

final class PlayerDistanceCursor {
    private final List<PrinterBox> boxes;
    private final PriorityQueue<BoxCursorNode> cursors = new PriorityQueue<>();
    private boolean complete;

    PlayerDistanceCursor(List<PrinterBox> boxes, int centerX, int centerY, int centerZ) {
        this(boxes, centerX, centerY, centerZ, Integer.MAX_VALUE);
    }

    PlayerDistanceCursor(List<PrinterBox> boxes, int centerX, int centerY, int centerZ, int maxDistanceBand) {
        this.boxes = boxes;
        for (int index = 0; index < boxes.size(); index++) {
            BoxDistanceCursor cursor = new BoxDistanceCursor(
                    boxes.get(index), centerX, centerY, centerZ, maxDistanceBand
            );
            BlockPos.MutableBlockPos first = new BlockPos.MutableBlockPos();
            if (cursor.next(first)) {
                this.cursors.add(new BoxCursorNode(
                        index,
                        cursor,
                        first.getX(),
                        first.getY(),
                        first.getZ(),
                        centerX,
                        centerY,
                        centerZ
                ));
            }
        }
        this.complete = this.cursors.isEmpty();
    }

    boolean isComplete() {
        return this.complete;
    }

    long peekDistanceSqr() {
        BoxCursorNode node = this.cursors.peek();
        return node == null ? Long.MAX_VALUE : node.distanceSqr();
    }

    boolean next(BlockPos.MutableBlockPos target) {
        while (!this.cursors.isEmpty()) {
            BoxCursorNode node = this.cursors.poll();
            int resultX = node.x;
            int resultY = node.y;
            int resultZ = node.z;
            if (node.cursor.next(node.following)) {
                node.x = node.following.getX();
                node.y = node.following.getY();
                node.z = node.following.getZ();
                this.cursors.add(node);
            }
            if (this.claimedByEarlierBox(node.boxIndex, resultX, resultY, resultZ)) {
                continue;
            }
            target.set(resultX, resultY, resultZ);
            return true;
        }
        this.complete = true;
        return false;
    }

    private boolean claimedByEarlierBox(int boxIndex, int x, int y, int z) {
        for (int index = 0; index < boxIndex; index++) {
            PrinterBox box = this.boxes.get(index);
            if (x >= box.minX && x <= box.maxX
                    && y >= box.minY && y <= box.maxY
                    && z >= box.minZ && z <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    private static final class BoxCursorNode implements Comparable<BoxCursorNode> {
        private final int boxIndex;
        private final BoxDistanceCursor cursor;
        private final int centerX;
        private final int centerY;
        private final int centerZ;
        private final BlockPos.MutableBlockPos following = new BlockPos.MutableBlockPos();
        private int x;
        private int y;
        private int z;

        private BoxCursorNode(
                int boxIndex,
                BoxDistanceCursor cursor,
                int x,
                int y,
                int z,
                int centerX,
                int centerY,
                int centerZ
        ) {
            this.boxIndex = boxIndex;
            this.cursor = cursor;
            this.x = x;
            this.y = y;
            this.z = z;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
        }

        @Override
        public int compareTo(BoxCursorNode other) {
            int result = Long.compare(this.distanceSqr(), other.distanceSqr());
            if (result != 0) {
                return result;
            }
            result = Integer.compare(this.x, other.x);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(this.y, other.y);
            if (result != 0) {
                return result;
            }
            result = Integer.compare(this.z, other.z);
            if (result != 0) {
                return result;
            }
            return Integer.compare(this.boxIndex, other.boxIndex);
        }

        private long distanceSqr() {
            long dx = this.x - (long) this.centerX;
            long dy = this.y - (long) this.centerY;
            long dz = this.z - (long) this.centerZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
