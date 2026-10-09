package com.raishxn.modern_manipulator.common.building.movers;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import com.raishxn.modern_manipulator.common.building.PendingMove;

public interface BlockMover<State> {

    boolean canMove(Level world, BlockPos pos);

    State remove(PendingMove pendingMove, Level world, BlockPos pos);

    void place(PendingMove pendingMove, Level world, BlockPos pos, State state);
}
