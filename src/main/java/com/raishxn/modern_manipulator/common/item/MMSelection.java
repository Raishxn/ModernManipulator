package com.raishxn.modern_manipulator.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import com.raishxn.modern_manipulator.common.item.MMState.Shape;
import org.jetbrains.annotations.Nullable;

public record MMSelection(ResourceLocation dimension, BlockPos min, BlockPos max, Shape shape) {

    public static @Nullable MMSelection from(MarkedPosition coordA, MarkedPosition coordB, Shape shape) {
        if (!coordA.dimension().equals(coordB.dimension())) {
            return null;
        }
        BlockPos a = coordA.pos();
        BlockPos b = coordB.pos();
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        return new MMSelection(coordA.dimension(), min, max, shape);
    }

    public int sizeX() {
        return max.getX() - min.getX() + 1;
    }

    public int sizeY() {
        return max.getY() - min.getY() + 1;
    }

    public int sizeZ() {
        return max.getZ() - min.getZ() + 1;
    }

    public long volume() {
        if (shape == Shape.CUBE) {
            return scanVolume();
        }
        long volume = 0L;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (contains(pos)) {
                volume++;
            }
        }
        return volume;
    }

    public long scanVolume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean isInPlayerRange(Player player, int maxRange) {
        if (maxRange < 0) {
            return true;
        }
        if (!player.level().dimension().location().equals(dimension)) {
            return false;
        }
        double maxRangeSquared = (double) maxRange * (double) maxRange;
        return player.blockPosition().distSqr(min) <= maxRangeSquared &&
                player.blockPosition().distSqr(max) <= maxRangeSquared;
    }

    public Iterable<BlockPos> positions() {
        return BlockPos.betweenClosed(min, max);
    }

    public boolean contains(BlockPos pos) {
        return switch (shape) {
            case LINE -> containsLine(pos);
            case CUBE -> true;
            case SPHERE -> containsSphere(pos);
            case CYLINDER -> containsCylinder(pos);
        };
    }

    public BlockPos positionAt(long index) {
        long layerSize = (long) sizeX() * sizeZ();
        long yOffset = index / layerSize;
        long layerIndex = index % layerSize;
        long zOffset = layerIndex / sizeX();
        long xOffset = layerIndex % sizeX();
        return new BlockPos(min.getX() + (int) xOffset, min.getY() + (int) yOffset, min.getZ() + (int) zOffset);
    }

    public String describe() {
        return "dX=" + sizeX() + " dY=" + sizeY() + " dZ=" + sizeZ() + " V=" + volume();
    }

    private boolean containsLine(BlockPos pos) {
        int dx = sizeX() - 1;
        int dy = sizeY() - 1;
        int dz = sizeZ() - 1;
        int steps = Math.max(dx, Math.max(dy, dz));
        if (steps == 0) {
            return pos.equals(min);
        }
        for (int i = 0; i <= steps; i++) {
            int x = min.getX() + Math.round((float) dx * i / steps);
            int y = min.getY() + Math.round((float) dy * i / steps);
            int z = min.getZ() + Math.round((float) dz * i / steps);
            if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSphere(BlockPos pos) {
        double radiusX = sizeX() / 2.0D;
        double radiusY = sizeY() / 2.0D;
        double radiusZ = sizeZ() / 2.0D;
        double centerX = min.getX() + radiusX - 0.5D;
        double centerY = min.getY() + radiusY - 0.5D;
        double centerZ = min.getZ() + radiusZ - 0.5D;
        double x = radiusX <= 0.5D ? 0.0D : (pos.getX() - centerX) / radiusX;
        double y = radiusY <= 0.5D ? 0.0D : (pos.getY() - centerY) / radiusY;
        double z = radiusZ <= 0.5D ? 0.0D : (pos.getZ() - centerZ) / radiusZ;
        return x * x + y * y + z * z <= 1.0D;
    }

    private boolean containsCylinder(BlockPos pos) {
        double radiusX = sizeX() / 2.0D;
        double radiusZ = sizeZ() / 2.0D;
        double centerX = min.getX() + radiusX - 0.5D;
        double centerZ = min.getZ() + radiusZ - 0.5D;
        double x = radiusX <= 0.5D ? 0.0D : (pos.getX() - centerX) / radiusX;
        double z = radiusZ <= 0.5D ? 0.0D : (pos.getZ() - centerZ) / radiusZ;
        return x * x + z * z <= 1.0D;
    }
}
