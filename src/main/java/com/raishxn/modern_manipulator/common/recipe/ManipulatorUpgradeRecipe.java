package com.raishxn.modern_manipulator.common.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMUpgrade;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;

public class ManipulatorUpgradeRecipe extends CustomRecipe {

    public ManipulatorUpgradeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return findMatch(container) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        Match match = findMatch(container);
        if (match == null) {
            return ItemStack.EMPTY;
        }

        ItemStack result = match.manipulatorStack.copy();
        result.setCount(1);
        MMState state = MatterManipulatorItem.getState(result);
        state.installUpgrade(match.upgrade);
        MatterManipulatorItem.setState(result, state);
        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MMRecipes.INSTALL_UPGRADE.get();
    }

    private Match findMatch(CraftingContainer container) {
        ItemStack manipulatorStack = ItemStack.EMPTY;
        MMUpgrade upgrade = null;

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            if (stack.getItem() instanceof MatterManipulatorItem) {
                if (!manipulatorStack.isEmpty()) {
                    return null;
                }
                manipulatorStack = stack;
                continue;
            }

            MMUpgrade stackUpgrade = MMUpgrade.byItem(stack.getItem());
            if (stackUpgrade != null) {
                if (upgrade != null) {
                    return null;
                }
                upgrade = stackUpgrade;
                continue;
            }

            return null;
        }

        if (manipulatorStack.isEmpty() || upgrade == null) {
            return null;
        }

        MatterManipulatorItem manipulator = (MatterManipulatorItem) manipulatorStack.getItem();
        MMState state = MatterManipulatorItem.getState(manipulatorStack);
        if (!upgrade.isAllowedOn(manipulator.tier()) || state.hasUpgrade(upgrade)) {
            return null;
        }

        return new Match(manipulatorStack, upgrade);
    }

    private record Match(ItemStack manipulatorStack, MMUpgrade upgrade) {}
}
