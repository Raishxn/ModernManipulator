package com.raishxn.modern_manipulator.common.items.manipulator;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3i;

import java.util.Objects;

/**
 * Represents a location in a world.
 * Can probably be improved, but it's not a big problem yet since these aren't meant to be kept around for very
 * long.
 */
public class Location {

    /** The dimension id (a resource location such as minecraft:overworld). */
    public String worldId;
    public int x, y, z;

    public Location() {}

    public Location(String worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Location(@NotNull Level world, int x, int y, int z) {
        this(getWorldId(world), x, y, z);
    }

    public Location(@NotNull Level world, Vector3i v) {
        this(world, v.x, v.y, v.z);
    }

    public Location(@NotNull Level world, BlockPos pos) {
        this(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public static String getWorldId(Level world) {
        return world.dimension().location().toString();
    }

    @Override
    public String toString() {
        return String.format("X=%,d Y=%,d Z=%,d", x, y, z);
    }

    public Vector3i toVec() {
        return new Vector3i(x, y, z);
    }

    public BlockPos toPos() {
        return new BlockPos(x, y, z);
    }

    public boolean isInWorld(@NotNull Level world) {
        return Objects.equals(getWorldId(world), worldId);
    }

    public int distanceTo2(Location other) {
        int dx = x - other.x;
        int dy = y - other.y;
        int dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double distanceTo(Location other) {
        return Math.sqrt(distanceTo2(other));
    }

    public ResourceKey<Level> getDimension() {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation(worldId));
    }

    public Level getWorld() {
        if (EffectiveSide.get().isClient()) {
            return getWorldClient();
        } else {
            var server = ServerLifecycleHooks.getCurrentServer();
            return server == null ? null : server.getLevel(getDimension());
        }
    }

    @OnlyIn(Dist.CLIENT)
    private Level getWorldClient() {
        Level world = Minecraft.getInstance().level;

        return world != null && isInWorld(world) ? world : null;
    }

    public Location offset(Direction dir) {
        this.x += dir.getStepX();
        this.y += dir.getStepY();
        this.z += dir.getStepZ();
        return this;
    }

    public Location offset(int dx, int dy, int dz) {
        this.x += dx;
        this.y += dy;
        this.z += dz;
        return this;
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public Location clone() {
        return new Location(worldId, x, y, z);
    }

    /**
     * Checks if two locations are compatible (in the same world).
     */
    public static boolean areCompatible(Location a, Location b) {
        if (a == null || b == null) return false;

        return Objects.equals(a.worldId, b.worldId);
    }

    /**
     * Checks if three locations are compatible (in the same world).
     */
    public static boolean areCompatible(Location a, Location b, Location c) {
        if (a == null || b == null || c == null) return false;

        if (!Objects.equals(a.worldId, b.worldId)) return false;
        return Objects.equals(a.worldId, c.worldId);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(worldId);
        result = prime * result + x;
        result = prime * result + y;
        result = prime * result + z;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Location other = (Location) obj;
        if (!Objects.equals(worldId, other.worldId)) return false;
        if (x != other.x) return false;
        if (y != other.y) return false;
        return z == other.z;
    }
}
