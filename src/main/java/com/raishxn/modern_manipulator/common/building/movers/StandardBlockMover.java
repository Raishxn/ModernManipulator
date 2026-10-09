package com.raishxn.modern_manipulator.common.building.movers;

import com.raishxn.modern_manipulator.common.building.PendingMove;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class StandardBlockMover implements BlockMover<StandardBlock> {

    public static final StandardBlockMover INSTANCE = new StandardBlockMover();

    /** Don't notify neighbours or drop items while the block is in transit. */
    private static final int MOVE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS |
        Block.UPDATE_MOVE_BY_PISTON;

    @Override
    public boolean canMove(Level world, BlockPos pos) {
        return true;
    }

    @Override
    public StandardBlock remove(PendingMove pendingMove, Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);

        CompoundTag tag = null;

        BlockEntity te = world.getBlockEntity(pos);

        if (te != null) {
            tag = te.saveWithoutMetadata();
        }

        // removing the block entity first prevents the block from dropping its contents
        world.removeBlockEntity(pos);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), MOVE_FLAGS);

        return new StandardBlock(state, tag);
    }

    @Override
    public void place(PendingMove pendingMove, Level world, BlockPos pos, StandardBlock standardBlock) {
        if (standardBlock.state().isAir()) return;

        world.setBlock(pos, standardBlock.state(), MOVE_FLAGS);

        if (standardBlock.tileData() != null) {
            BlockEntity te = world.getBlockEntity(pos);

            if (te == null) {
                te = BlockEntity.loadStatic(pos, standardBlock.state(), standardBlock.tileData());

                if (te != null) world.setBlockEntity(te);
            } else {
                te.load(standardBlock.tileData());
            }

            if (te != null) {
                te.setChanged();
            }
        }

        world.sendBlockUpdated(pos, standardBlock.state(), standardBlock.state(), Block.UPDATE_ALL);
    }
}
