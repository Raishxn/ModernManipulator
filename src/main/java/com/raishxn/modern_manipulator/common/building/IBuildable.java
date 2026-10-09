package com.raishxn.modern_manipulator.common.building;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Something that can be built.
 */
public interface IBuildable {

    void tryPlaceBlocks(ItemStack stack, Player player);

    void onStopped();
}
