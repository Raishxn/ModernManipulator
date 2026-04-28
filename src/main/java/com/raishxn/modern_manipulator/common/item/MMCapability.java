package com.raishxn.modern_manipulator.common.item;

import net.minecraft.network.chat.Component;

public enum MMCapability {

    CONNECTS_TO_AE("connects_to_ae"),
    CONNECTS_TO_UPLINK("connects_to_uplink"),
    ALLOW_REMOVING("allow_removing"),
    ALLOW_GEOMETRY("allow_geometry"),
    ALLOW_CONFIGURING("allow_configuring"),
    ALLOW_COPYING("allow_copying"),
    ALLOW_EXCHANGING("allow_exchanging"),
    ALLOW_MOVING("allow_moving"),
    ALLOW_CABLES("allow_cables"),
    ALLOW_SMART_COPY("allow_smart_copy");

    private final String serializedName;
    private final int mask;

    MMCapability(String serializedName) {
        this.serializedName = serializedName;
        this.mask = 1 << ordinal();
    }

    public int mask() {
        return mask;
    }

    public Component displayName() {
        return Component.translatable("matter_manipulator.capability." + serializedName);
    }
}
