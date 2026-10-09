package com.raishxn.modern_manipulator.common.building.movers;

import com.raishxn.modern_manipulator.common.building.PendingMove;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public interface BlockMover<State> {

    boolean canMove(Level world, BlockPos pos);

    State remove(PendingMove pendingMove, Level world, BlockPos pos);

    void place(PendingMove pendingMove, Level world, BlockPos pos, State state);
}
