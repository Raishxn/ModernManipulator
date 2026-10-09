package com.raishxn.modern_manipulator;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

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
}
