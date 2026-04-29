package com.raishxn.modern_manipulator.common.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.raishxn.modern_manipulator.common.item.MMState.Blueprint;
import com.raishxn.modern_manipulator.common.item.MMState.BlueprintBlock;

import java.util.ArrayList;
import java.util.List;

public final class BlueprintMaterials {

    private BlueprintMaterials() {}

    public static List<ItemStack> requiredItems(Blueprint blueprint) {
        return requiredItems(blueprint, 1L);
    }

    public static List<ItemStack> requiredItems(Blueprint blueprint, long multiplier) {
        if (!blueprint.consumesItems()) {
            return List.of();
        }

        List<ItemStack> result = new ArrayList<>();
        for (BlueprintBlock block : blueprint.blocks()) {
            Item item = block.state().getBlock().asItem();
            if (item == Items.AIR) {
                continue;
            }
            mergeStack(result, withCount(new ItemStack(item), multiplier));
            for (Tag tag : BlockMovers.additionalRequiredItems(block.blockEntityTag())) {
                if (tag instanceof CompoundTag compound) {
                    ItemStack required = ItemStack.of(compound);
                    if (!required.isEmpty()) {
                        mergeStack(result, withCount(required, multiplier));
                    }
                }
            }
        }
        return result;
    }

    private static ItemStack withCount(ItemStack stack, long multiplier) {
        ItemStack copy = stack.copy();
        long count = (long) copy.getCount() * Math.max(1L, multiplier);
        copy.setCount(count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count);
        return copy;
    }

    private static void mergeStack(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack existingStack : stacks) {
            if (ItemStack.isSameItemSameTags(existingStack, stack)) {
                existingStack.grow(stack.getCount());
                return;
            }
        }
        stacks.add(stack.copy());
    }
}
