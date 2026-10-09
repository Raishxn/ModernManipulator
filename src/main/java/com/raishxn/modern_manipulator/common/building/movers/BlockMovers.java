package com.raishxn.modern_manipulator.common.building.movers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public class BlockMovers {

    private static final List<BlockMover<?>> MOVERS = new ArrayList<>();

    private BlockMovers() {}

    /** Registers a mover with a higher priority than the existing ones. */
    public static void register(BlockMover<?> mover) {
        MOVERS.add(0, mover);
    }

    public static BlockMover<?> getBlockMover(Level world, BlockPos pos) {
        for (BlockMover<?> mover : MOVERS) {
            if (mover.canMove(world, pos)) return mover;
        }

        return StandardBlockMover.INSTANCE;
    }
}
