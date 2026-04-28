package com.raishxn.modern_manipulator.common.recipe;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.raishxn.modern_manipulator.ModernManipulator;

public final class MMRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(
            ForgeRegistries.RECIPE_SERIALIZERS, ModernManipulator.MOD_ID);

    public static final RegistryObject<RecipeSerializer<ManipulatorUpgradeRecipe>> INSTALL_UPGRADE = SERIALIZERS
            .register("install_upgrade", () -> new SimpleCraftingRecipeSerializer<>(ManipulatorUpgradeRecipe::new));

    private MMRecipes() {}
}
