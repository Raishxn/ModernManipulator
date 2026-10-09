package com.raishxn.modern_manipulator.common.utils;

import net.minecraftforge.fml.ModList;

public enum Mods {

    GregTech(Names.GREG_TECH),
    AppliedEnergistics2(Names.APPLIED_ENERGISTICS2),
    ExtendedAE(Names.EXTENDED_AE),
    EnderStorage(Names.ENDER_STORAGE),
    Mekanism(Names.MEKANISM),
    ;

    public static class Names {

        public static final String MATTER_MANIPULATOR = "modern_manipulator";
        public static final String GREG_TECH = "gtceu";
        public static final String APPLIED_ENERGISTICS2 = "ae2";
        public static final String EXTENDED_AE = "expatternprovider";
        public static final String ENDER_STORAGE = "enderstorage";
        public static final String MEKANISM = "mekanism";
    }

    public final String ID;
    private Boolean loaded;

    Mods(String ID) {
        this.ID = ID;
    }

    public boolean isModLoaded() {
        if (loaded == null) {
            ModList list = ModList.get();
            if (list == null) return false;
            loaded = list.isLoaded(ID);
        }
        return loaded;
    }
}
