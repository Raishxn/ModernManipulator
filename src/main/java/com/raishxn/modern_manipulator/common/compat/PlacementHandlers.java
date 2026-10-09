package com.raishxn.modern_manipulator.common.compat;

import com.raishxn.modern_manipulator.common.building.IBlockApplyContext;
import com.raishxn.modern_manipulator.common.building.PendingBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Integrations that need to place blocks in a special way (ae parts in cable busses, etc).
 */
public class PlacementHandlers {

    public interface IPlacementHandler {

        /**
         * Places the pending block.
         *
         * @return True when this handler placed the block, false to let the next handler (or the default logic) handle
         *         it.
         */
        boolean place(Level world, BlockPos pos, PendingBlock pending, ItemStack stack, Player player, IBlockApplyContext context);
    }

    private static final List<IPlacementHandler> HANDLERS = new ArrayList<>();

    private PlacementHandlers() {}

    public static void register(IPlacementHandler handler) {
        HANDLERS.add(handler);
    }

    public static boolean place(Level world, BlockPos pos, PendingBlock pending, ItemStack stack, Player player, IBlockApplyContext context) {
        for (IPlacementHandler handler : HANDLERS) {
            if (handler.place(world, pos, pending, stack, player, context)) return true;
        }

        return false;
    }
}
