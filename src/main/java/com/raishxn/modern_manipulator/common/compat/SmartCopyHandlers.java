package com.raishxn.modern_manipulator.common.compat;

import net.minecraft.world.level.Level;

import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;

import java.util.ArrayList;
import java.util.List;

/**
 * Smart copy integrations (CRIB to proxy, interface to P2P).
 */
public class SmartCopyHandlers {

    public interface ISmartCopyHandler {

        void apply(MMState state, Level world, List<PendingBlock> blocks, Location coordA);
    }

    private static final List<ISmartCopyHandler> HANDLERS = new ArrayList<>();

    private SmartCopyHandlers() {}

    public static void register(ISmartCopyHandler handler) {
        HANDLERS.add(handler);
    }

    public static void apply(MMState state, Level world, List<PendingBlock> blocks, Location coordA) {
        for (ISmartCopyHandler handler : HANDLERS) {
            handler.apply(state, world, blocks, coordA);
        }
    }
}
