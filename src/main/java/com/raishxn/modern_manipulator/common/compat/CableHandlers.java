package com.raishxn.modern_manipulator.common.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.ImmutableBlockSpec;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;

/**
 * Cable mode integrations (GregTech pipes/cables, AE2 cables, ...).
 */
public class CableHandlers {

    public interface ICableHandler {

        /**
         * Picks the cable at the given position into spec.
         *
         * @return True if a cable was found
         */
        boolean pickCable(BlockSpec spec, Level world, BlockPos pos);

        /**
         * Replaces the given world spec with the cable contained by the block, if this block contains a cable that
         * isn't a block by itself (such as an ae cable in a cable bus).
         */
        default boolean getCableInWorld(BlockSpec spec, Level world, BlockPos pos) {
            return false;
        }

        /**
         * Creates the pending block used to exchange a block for the given replacement, if this handler needs special
         * logic.
         */
        default @Nullable PendingBlock instantiateExchange(MMState state, ImmutableBlockSpec replacement, Level world,
                                                           BlockPos pos) {
            return null;
        }

        /**
         * Gets the pending block that removes the cable at the given position (when the cable spec is null).
         */
        default @Nullable PendingBlock getCableRemoval(Level world, BlockPos pos) {
            return null;
        }

        /**
         * Generates the cable line.
         *
         * @return True when this handler handled the cable spec
         */
        boolean getCables(Vector3i a, Vector3i b, List<Vector3i> voxels, List<PendingBlock> out, Level world,
                          ImmutableBlockSpec cable);
    }

    private static final List<ICableHandler> HANDLERS = new ArrayList<>();

    private CableHandlers() {}

    public static void register(ICableHandler handler) {
        HANDLERS.add(handler);
    }

    public static boolean pickCable(BlockSpec spec, Level world, BlockPos pos) {
        for (ICableHandler handler : HANDLERS) {
            if (handler.pickCable(spec, world, pos)) return true;
        }

        return false;
    }

    public static boolean getCableInWorld(BlockSpec spec, Level world, BlockPos pos) {
        for (ICableHandler handler : HANDLERS) {
            if (handler.getCableInWorld(spec, world, pos)) return true;
        }

        return false;
    }

    public static @Nullable PendingBlock instantiateExchange(MMState state, ImmutableBlockSpec replacement, Level world,
                                                             BlockPos pos) {
        for (ICableHandler handler : HANDLERS) {
            PendingBlock block = handler.instantiateExchange(state, replacement, world, pos);
            if (block != null) return block;
        }

        return null;
    }

    public static @Nullable PendingBlock getCableRemoval(Level world, BlockPos pos) {
        for (ICableHandler handler : HANDLERS) {
            PendingBlock block = handler.getCableRemoval(world, pos);
            if (block != null) return block;
        }

        return null;
    }

    public static void getCables(Vector3i a, Vector3i b, List<Vector3i> voxels, List<PendingBlock> out, Level world,
                                 ImmutableBlockSpec cable) {
        for (ICableHandler handler : HANDLERS) {
            if (handler.getCables(a, b, voxels, out, world, cable)) return;
        }

        // generic fallback: place the block along the line
        for (Vector3i voxel : voxels) {
            out.add(cable.instantiate(world, voxel.x, voxel.y, voxel.z));
        }
    }
}
