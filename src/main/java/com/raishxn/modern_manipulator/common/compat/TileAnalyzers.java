package com.raishxn.modern_manipulator.common.compat;

import net.minecraft.world.level.block.entity.BlockEntity;

import com.raishxn.modern_manipulator.common.building.PendingBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of block entity analyzers (GregTech, AE2, ...). Each analyzer fills in its own field on the pending block.
 */
public class TileAnalyzers {

    public interface ITileAnalyzer {

        /** @param flags the PendingBlock.ANALYZE_* flags */
        void analyze(PendingBlock block, BlockEntity te, int flags);
    }

    private static final List<ITileAnalyzer> ANALYZERS = new ArrayList<>();

    private TileAnalyzers() {}

    public static void register(ITileAnalyzer analyzer) {
        ANALYZERS.add(analyzer);
    }

    public static void analyze(PendingBlock block, BlockEntity te, int flags) {
        for (ITileAnalyzer analyzer : ANALYZERS) {
            analyzer.analyze(block, te, flags);
        }
    }
}
