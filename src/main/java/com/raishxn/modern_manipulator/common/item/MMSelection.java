package com.raishxn.modern_manipulator.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import org.jetbrains.annotations.Nullable;

public record MMSelection(ResourceLocation dimension, BlockPos min, BlockPos max) {

    public static @Nullable MMSelection from(MarkedPosition coordA, MarkedPosition coordB) {
        if (!coordA.dimension().equals(coordB.dimension())) {
            return null;
        }
        BlockPos a = coordA.pos();
        BlockPos b = coordB.pos();
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ()));
        return new MMSelection(coordA.dimension(), min, max);
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

    public String describe() {
        return "dX=" + sizeX() + " dY=" + sizeY() + " dZ=" + sizeZ() + " V=" + volume();
    }
}
