package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.compat.ae.AECompat;
import com.raishxn.modern_manipulator.common.utils.Mods;

import net.minecraft.world.item.Item;

/**
 * Small item helpers that dispatch to optional integrations without loading their classes.
 */
public class ItemSpecHelpers {

    private ItemSpecHelpers() {}

    public static boolean isAEPart(Item item) {
        return Mods.AppliedEnergistics2.isModLoaded() && AECompat.isPartItem(item);
    }
}
