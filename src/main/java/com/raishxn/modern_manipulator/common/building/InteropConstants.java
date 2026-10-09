package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import com.raishxn.modern_manipulator.common.utils.Mods;

/**
 * Various constants or static methods used for interop.
 */
public class InteropConstants {

    private InteropConstants() {}

    public static final ResourceLocation AE_CABLE_BUS = new ResourceLocation("ae2", "cable_bus");

    private static Block aeCableBus;

    public static Block getAECableBus() {
        if (aeCableBus == null) {
            aeCableBus = Mods.AppliedEnergistics2.isModLoaded() ? BuiltInRegistries.BLOCK.get(AE_CABLE_BUS) :
                    Blocks.AIR;
        }

        return aeCableBus;
    }

    public static boolean isAECableBus(Block block) {
        return Mods.AppliedEnergistics2.isModLoaded() && block != Blocks.AIR && block == getAECableBus();
    }

    public static boolean isAir(BlockState state) {
        return state.isAir();
    }

    public static boolean skipWhenCopying(BlockState state) {
        if (state.getBlock() instanceof LiquidBlock) return true;
        if (state.is(Blocks.LIGHT)) return true;
        if (state.is(Blocks.MOVING_PISTON)) return true;
        if (BlockSpec.isSecondaryHalf(state)) return true;

        return false;
    }

    public static boolean shouldDropItem(BlockState state) {
        if (state.is(Blocks.LIGHT)) return false;

        return true;
    }

    public static boolean isFree(BlockState state) {
        if (state.isAir()) return true;

        if (isAECableBus(state.getBlock())) return true;

        return false;
    }
}
