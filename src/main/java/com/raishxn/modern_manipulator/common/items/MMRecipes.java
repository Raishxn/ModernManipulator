package com.raishxn.modern_manipulator.common.items;

import com.raishxn.modern_manipulator.ModernManipulator;

import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MMRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS,
        ModernManipulator.MOD_ID);

    public static final RegistryObject<RecipeSerializer<RecipeInstallUpgrade>> INSTALL_UPGRADE = SERIALIZERS
        .register("install_upgrade", () -> new SimpleCraftingRecipeSerializer<>(RecipeInstallUpgrade::new));

    private MMRecipes() {}
}
