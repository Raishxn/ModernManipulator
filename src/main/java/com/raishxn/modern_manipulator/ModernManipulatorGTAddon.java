package com.raishxn.modern_manipulator;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

@GTAddon
@SuppressWarnings("unused")
public class ModernManipulatorGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return ModernManipulator.REGISTRATE;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return ModernManipulator.MOD_ID;
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        // GTCEu recipe chains will be ported after the base items and state format are stable.
    }
}
