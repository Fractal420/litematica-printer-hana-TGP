package me.aleksilassila.litematica.printer.handler.scan;

import me.aleksilassila.litematica.printer.printer.PrinterBox;
import net.minecraft.core.BlockPos;

final class BoxDistanceCursor {
    private static final int STATE_BITS = 21;
    private static final long STATE_MASK = (1L << STATE_BITS) - 1L;

    private final int[] xCoordinates;
    private final int[] yCoordinates;
    private final int[] zCoordinates;
    private final long[] xDistanceSqr;
    private final long[] yDistanceSqr;
    private final long[] zDistanceSqr;
    private final long maxDistanceSqr;
    private long[] heap = new long[64];
    private int heapSize;
    private boolean complete;
    private long probeCount;

    BoxDistanceCursor(PrinterBox box, int centerX, int centerY, int centerZ) {
        this(box, centerX, centerY, centerZ, Integer.MAX_VALUE);
    }

    BoxDistanceCursor(PrinterBox box, int centerX, int centerY, int centerZ, int maxDistanceBand) {
        this.xCoordinates = buildAxisCoordinates(box.minX, box.maxX, centerX);
        this.yCoordinates = buildAxisCoordinates(box.minY, box.maxY, centerY);
        this.zCoordinates = buildAxisCoordinates(box.minZ, box.maxZ, centerZ);
        this.xDistanceSqr = buildAxisDistances(this.xCoordinates, centerX);
        this.yDistanceSqr = buildAxisDistances(this.yCoordinates, centerY);
        this.zDistanceSqr = buildAxisDistances(this.zCoordinates, centerZ);
        this.maxDistanceSqr = maxDistanceBand == Integer.MAX_VALUE
                ? Long.MAX_VALUE
                : (long) maxDistanceBand * maxDistanceBand;
        if (this.xCoordinates.length == 0 || this.yCoordinates.length == 0 || this.zCoordinates.length == 0) {
            this.complete = true;
        } else {
            this.push(packState(0, 0, 0));
        }
    }

    boolean next(BlockPos.MutableBlockPos target) {
        while (!this.complete && this.heapSize > 0) {
            long state = this.pop();
            int xIndex = xIndex(state);
            int yIndex = yIndex(state);
            int zIndex = zIndex(state);

            if (xIndex + 1 < this.xCoordinates.length) {
                this.push(packState(xIndex + 1, yIndex, zIndex));
            }
            if (xIndex == 0 && yIndex + 1 < this.yCoordinates.length) {
                this.push(packState(0, yIndex + 1, zIndex));
            }
            if (xIndex == 0 && yIndex == 0 && zIndex + 1 < this.zCoordinates.length) {
                this.push(packState(0, 0, zIndex + 1));
            }

            target.set(
                    this.xCoordinates[xIndex],
                    this.yCoordinates[yIndex],
                    this.zCoordinates[zIndex]
            );
            return true;
        }
        if (this.heapSize == 0) {
            this.complete = true;
        }
        return false;
    }

    long probeCount() {
        return this.probeCount;
    }

    private void push(long state) {
        this.probeCount++;
        if (this.distanceSqr(state) > this.maxDistanceSqr) {

            return;
        }
        if (this.heapSize >= this.heap.length) {
            long[] expanded = new long[this.heap.length << 1];
            System.arraycopy(this.heap, 0, expanded, 0, this.heap.length);
            this.heap = expanded;
        }
        int index = this.heapSize++;
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            long parentState = this.heap[parent];
            if (this.compare(parentState, state) <= 0) {
                break;
            }
            this.heap[index] = parentState;
            index = parent;
        }
        this.heap[index] = state;
    }

    private long pop() {
        this.probeCount++;
        long result = this.heap[0];
        long tail = this.heap[--this.heapSize];
        if (this.heapSize == 0) {
            return result;
        }

        int index = 0;
        int half = this.heapSize >>> 1;
        while (index < half) {
            int left = (index << 1) + 1;
            int right = left + 1;
            int child = left;
            if (right < this.heapSize && this.compare(this.heap[right], this.heap[left]) < 0) {
                child = right;
            }
            if (this.compare(tail, this.heap[child]) <= 0) {
                break;
            }
            this.heap[index] = this.heap[child];
            index = child;
        }
        this.heap[index] = tail;
        return result;
    }

    private int compare(long left, long right) {
        long leftDistance = this.distanceSqr(left);
        long rightDistance = this.distanceSqr(right);
        int result = Long.compare(leftDistance, rightDistance);
        if (result != 0) {
            return result;
        }

        long leftMaxAxisDistance = this.maxAxisDistanceSqr(left);
        long rightMaxAxisDistance = this.maxAxisDistanceSqr(right);
        result = Long.compare(leftMaxAxisDistance, rightMaxAxisDistance);
        if (result != 0) {
            return result;
        }
        return Long.compareUnsigned(left, right);
    }

    private long distanceSqr(long state) {
        return this.xDistanceSqr[xIndex(state)]
                + this.yDistanceSqr[yIndex(state)]
                + this.zDistanceSqr[zIndex(state)];
    }

    private long maxAxisDistanceSqr(long state) {
        return Math.max(
                Math.max(this.xDistanceSqr[xIndex(state)], this.yDistanceSqr[yIndex(state)]),
                this.zDistanceSqr[zIndex(state)]
        );
    }

    private static int[] buildAxisCoordinates(int min, int max, int center) {
        if (max < min) {
            return new int[0];
        }
        int[] coordinates = new int[max - min + 1];
        int pivot = Math.max(min, Math.min(max, center));
        int left = pivot;
        int right = pivot + 1;
        int index = 0;
        while (left >= min || right <= max) {
            if (left < min) {
                coordinates[index++] = right++;
                continue;
            }
            if (right > max) {
                coordinates[index++] = left--;
                continue;
            }
            long leftDistance = axisDistanceSqr(left, center);
            long rightDistance = axisDistanceSqr(right, center);
            if (leftDistance <= rightDistance) {
                coordinates[index++] = left--;
            } else {
                coordinates[index++] = right++;
            }
        }
        return coordinates;
    }

    private static long[] buildAxisDistances(int[] coordinates, int center) {
        long[] distances = new long[coordinates.length];
        for (int index = 0; index < coordinates.length; index++) {
            distances[index] = axisDistanceSqr(coordinates[index], center);
        }
        return distances;
    }

    private static long axisDistanceSqr(int coordinate, int center) {
        long delta = coordinate - (long) center;
        return delta * delta;
    }

    private static long packState(int xIndex, int yIndex, int zIndex) {
        return (long) xIndex << STATE_BITS * 2
                | (long) yIndex << STATE_BITS
                | zIndex;
    }

    private static int xIndex(long state) {
        return (int) (state >>> STATE_BITS * 2);
    }

    private static int yIndex(long state) {
        return (int) (state >>> STATE_BITS & STATE_MASK);
    }

    private static int zIndex(long state) {
        return (int) (state & STATE_MASK);
    }
}
