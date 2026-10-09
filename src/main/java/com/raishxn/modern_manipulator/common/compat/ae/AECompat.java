package com.raishxn.modern_manipulator.common.compat.ae;

import net.minecraft.world.item.Item;

import appeng.api.parts.IPartItem;

/**
 * AE2 helpers. Only load this class when AE2 is present.
 */
public class AECompat {

    private AECompat() {}

    public static boolean isPartItem(Item item) {
        return item instanceof IPartItem<?>;
    }
}
