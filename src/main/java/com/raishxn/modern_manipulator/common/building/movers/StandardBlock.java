package com.raishxn.modern_manipulator.common.building.movers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

public record StandardBlock(BlockState state, @Nullable CompoundTag tileData) {}
