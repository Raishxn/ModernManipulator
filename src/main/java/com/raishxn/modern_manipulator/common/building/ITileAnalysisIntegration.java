package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public interface ITileAnalysisIntegration {

    boolean apply(IBlockApplyContext ctx);

    boolean getRequiredItemsForExistingBlock(IBlockApplyContext context);

    boolean getRequiredItemsForNewBlock(IBlockApplyContext context);

    void getItemTag(ItemStack stack);

    void getItemDetailsChat(List<Component> details);

    void transform(Transform transform);

    ITileAnalysisIntegration clone();

    void migrate();

    /** The block state shown in the preview, if this integration wants to override it. */
    default BlockState getPreviewState() {
        return null;
    }

    /** An item to show in the preview instead of the block (ae parts in a cable bus, etc). */
    default ItemStack getPreviewStack() {
        return null;
    }
}
