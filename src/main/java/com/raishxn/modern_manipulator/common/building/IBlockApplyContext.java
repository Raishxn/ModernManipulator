package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The context within which analysis results are applied.
 * Contains everything needed to apply a TileAnalysisResult.
 */
public interface IBlockApplyContext extends IPseudoInventory {

    Level getWorld();

    int getX();

    int getY();

    int getZ();

    default BlockPos getPos() {
        return new BlockPos(getX(), getY(), getZ());
    }

    BlockEntity getTileEntity();

    Player getRealPlayer();

    boolean tryApplyAction(double complexity);

    void warn(Component message);

    void error(Component message);
}
