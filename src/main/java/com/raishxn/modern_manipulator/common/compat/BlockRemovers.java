package com.raishxn.modern_manipulator.common.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.raishxn.modern_manipulator.common.building.AbstractBuildable;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrations that reset a block before it's removed (remove covers, empty ME hatches, remove ae parts...).
 */
public class BlockRemovers {

    public interface IBlockRemover {

        void reset(AbstractBuildable buildable, BlockState state, BlockEntity te);
    }

    public interface IOreChecker {

        boolean isOre(BlockState state);
    }

    private static final List<java.util.function.Predicate<BlockEntity>> TANK_BLACKLIST = new ArrayList<>();

    /** Registers a predicate for block entities whose tanks shouldn't be emptied (stocking hatches, part hosts). */
    public static void registerTankBlacklist(java.util.function.Predicate<BlockEntity> predicate) {
        TANK_BLACKLIST.add(predicate);
    }

    public static boolean shouldEmptyTanks(BlockEntity te) {
        for (var predicate : TANK_BLACKLIST) {
            if (predicate.test(te)) return false;
        }

        return true;
    }

    private static final List<IBlockRemover> REMOVERS = new ArrayList<>();
    private static final List<IOreChecker> ORE_CHECKERS = new ArrayList<>();

    private BlockRemovers() {}

    public static void register(IBlockRemover remover) {
        REMOVERS.add(remover);
    }

    public static void registerOreChecker(IOreChecker checker) {
        ORE_CHECKERS.add(checker);
    }

    public static void reset(AbstractBuildable buildable, BlockState state, BlockEntity te) {
        for (IBlockRemover remover : REMOVERS) {
            remover.reset(buildable, state, te);
        }
    }

    public static boolean isOre(BlockState state) {
        for (IOreChecker checker : ORE_CHECKERS) {
            if (checker.isOre(state)) return true;
        }

        return false;
    }
}
