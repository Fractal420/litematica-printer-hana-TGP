package me.aleksilassila.litematica.printer.printer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

import net.minecraft.core.BlockPos;

public class PrinterBox implements Iterable<BlockPos> {
    public static final Minecraft client = Minecraft.getInstance();
    public final int minX, minY, minZ;
    public final int maxX, maxY, maxZ;

    public PrinterBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxZ = Math.max(minZ, maxZ);
        int rawMinY = Math.min(minY, maxY);
        int rawMaxY = Math.max(minY, maxY);
        if (client != null && client.level != null) {
            this.minY = Math.max(client.level.getMinY(), rawMinY);
            this.maxY = Math.min(client.level.getMaxY(), rawMaxY);
        } else {
            this.minY = rawMinY;
            this.maxY = rawMaxY;
        }
    }

    public PrinterBox(BlockPos pos) {
        this(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public PrinterBox(Vec3i pos1, Vec3i pos2) {
        this(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
    }

    public boolean contains(int x, int y, int z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    public boolean contains(Vec3i vec3i) {
        return vec3i.getX() >= this.minX && vec3i.getX() <= this.maxX && vec3i.getY() >= this.minY && vec3i.getY() <= this.maxY && vec3i.getZ() >= this.minZ && vec3i.getZ() <= this.maxZ;
    }

    public boolean sameSectionWindow(PrinterBox other) {
        return sectionCoord(this.minX) == sectionCoord(other.minX)
                && sectionCoord(this.maxX) == sectionCoord(other.maxX)
                && sectionCoord(this.minY) == sectionCoord(other.minY)
                && sectionCoord(this.maxY) == sectionCoord(other.maxY)
                && sectionCoord(this.minZ) == sectionCoord(other.minZ)
                && sectionCoord(this.maxZ) == sectionCoord(other.maxZ);
    }

    public PrinterBox expand(int expandX, int expandY, int expandZ) {
        int minX = this.minX - expandX;
        int minZ = this.minZ - expandZ;
        int maxX = this.maxX + expandX;
        int maxZ = this.maxZ + expandZ;
        int minY = this.minY - expandY;
        int maxY = this.maxY + expandY;
        if (client != null && client.level != null) {
            minY = Math.max(client.level.getMinY(), minY);
            maxY = Math.min(client.level.getMaxY(), maxY);
        }
        return new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public PrinterBox expand(int value) {
        return this.expand(value, value, value);
    }

    @Override
    public @NotNull Iterator<BlockPos> iterator() {
        return new BoxIterator();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PrinterBox box = (PrinterBox) o;
        return minX == box.minX && minY == box.minY && minZ == box.minZ && maxX == box.maxX && maxY == box.maxY && maxZ == box.maxZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private class BoxIterator implements Iterator<BlockPos> {
        public BlockPos currPos;

        @Override
        public boolean hasNext() {
            if (currPos == null) return true;
            int x = currPos.getX();
            int y = currPos.getY();
            int z = currPos.getZ();

            return !(x == maxX && y == maxY && z == maxZ);
        }

        @Override
        public BlockPos next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            if (currPos == null) {
                currPos = new BlockPos(
                        minX,
                        minY,
                        minZ
                );
                return currPos;
            }

            int x = currPos.getX();
            int y = currPos.getY();
            int z = currPos.getZ();

            x++;
            if (x > maxX) {
                x = minX;
                z++;
                if (z > maxZ) {
                    z = minZ;
                    y++;
                }
            }

            currPos = new BlockPos(x, y, z);
            return currPos;
        }
    }

    private static int sectionCoord(int blockCoord) {
        return blockCoord >> 4;
    }
}
