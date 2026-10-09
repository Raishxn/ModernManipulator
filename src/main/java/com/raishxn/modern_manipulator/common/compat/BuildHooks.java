package com.raishxn.modern_manipulator.common.compat;

import com.raishxn.modern_manipulator.common.building.IBlockApplyContext;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.building.PendingBuild;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Hooks into the build process (wireless link fixing, smart copy source P2P placement, ...).
 */
public class BuildHooks {

    public interface IBuildHook {

        default void onBuildTick(PendingBuild build, Level world) {}

        default void onBlockApplied(PendingBuild build, Level world, BlockPos pos, PendingBlock block) {}

        default void onBlocksPlaced(PendingBuild build, Level world, List<PendingBlock> placed, IBlockApplyContext context) {}

        /** When true, an equivalent existing block will still be removed and replaced. */
        default boolean requiresReplacement(Level world, BlockPos pos, PendingBlock block) {
            return false;
        }
    }

    private static final List<IBuildHook> HOOKS = new ArrayList<>();

    private BuildHooks() {}

    public static void register(IBuildHook hook) {
        HOOKS.add(hook);
    }

    public static void onBuildTick(PendingBuild build, Level world) {
        for (IBuildHook hook : HOOKS) hook.onBuildTick(build, world);
    }

    public static void onBlockApplied(PendingBuild build, Level world, BlockPos pos, PendingBlock block) {
        for (IBuildHook hook : HOOKS) hook.onBlockApplied(build, world, pos, block);
    }

    public static void onBlocksPlaced(PendingBuild build, Level world, List<PendingBlock> placed, IBlockApplyContext context) {
        for (IBuildHook hook : HOOKS) hook.onBlocksPlaced(build, world, placed, context);
    }

    public static boolean requiresReplacement(Level world, BlockPos pos, PendingBlock block) {
        for (IBuildHook hook : HOOKS) {
            if (hook.requiresReplacement(world, pos, block)) return true;
        }

        return false;
    }
}
