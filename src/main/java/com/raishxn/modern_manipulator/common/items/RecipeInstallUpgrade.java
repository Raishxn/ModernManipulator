package com.raishxn.modern_manipulator.common.items;

import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class RecipeInstallUpgrade extends CustomRecipe {

    public RecipeInstallUpgrade(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer inv, Level world) {
        if (getItemCount(inv) > 2) return false;

        var manipulator = findManipulator(inv);

        if (manipulator == null) return false;

        var upgrade = findUpgrade(inv, manipulator);

        return upgrade != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        if (getItemCount(inv) > 2) return ItemStack.EMPTY;

        var manipulator = findManipulator(inv);

        if (manipulator == null) return ItemStack.EMPTY;

        var upgrade = findUpgrade(inv, manipulator);

        if (upgrade == null) return ItemStack.EMPTY;

        ItemStack stack = manipulator.stack.copyWithCount(1);

        manipulator.state.installUpgrade(upgrade.upgrade);

        ItemMatterManipulator.setState(stack, manipulator.state);

        return stack;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return MMRecipes.INSTALL_UPGRADE.get();
    }

    private static int getItemCount(CraftingContainer inv) {
        int count = 0;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty()) count++;
        }

        return count;
    }

    private record ManipulatorInfo(ItemStack stack, int slot, MMState state, ItemMatterManipulator.ManipulatorTier tier) {}

    private static ManipulatorInfo findManipulator(CraftingContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);

            if (stack.isEmpty()) continue;

            if (!(stack.getItem() instanceof ItemMatterManipulator manipulator)) continue;

            return new ManipulatorInfo(stack, i, ItemMatterManipulator.getState(stack), manipulator.tier);
        }

        return null;
    }

    private record UpgradeInfo(ItemStack stack, int slot, MMUpgrades upgrade) {}

    private static UpgradeInfo findUpgrade(CraftingContainer inv, ManipulatorInfo manipulator) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);

            if (stack.isEmpty()) continue;

            MMUpgrades upgrade = MMUpgrades.byItem(stack.getItem());

            if (upgrade == null) continue;

            if (!manipulator.state.couldAcceptUpgrade(manipulator.tier, upgrade)) continue;

            return new UpgradeInfo(stack, i, upgrade);
        }

        return null;
    }
}
