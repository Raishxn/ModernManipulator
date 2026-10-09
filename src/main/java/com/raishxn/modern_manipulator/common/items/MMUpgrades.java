package com.raishxn.modern_manipulator.common.items;

import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

public enum MMUpgrades {

    PowerP2P(() -> MMItems.ENERGY_TUNNEL_UPGRADE.get(), 0, 0),
    Mining(() -> MMItems.EXCAVATION_UPGRADE.get(), 1, ItemMatterManipulator.ALLOW_REMOVING),
    Speed(() -> MMItems.AUXILIARY_TELEPORTER_UPGRADE.get(), 2, 0),
    PowerEff(() -> MMItems.ADAPTIVE_WIRING_HARNESS_UPGRADE.get(), 3, 0),
    //
    ;

    private final Supplier<Item> item;
    public final int bit;
    public final int providesCaps;

    MMUpgrades(Supplier<Item> item, int bit, int providesCaps) {
        this.item = item;
        this.bit = bit;
        this.providesCaps = providesCaps;
    }

    public Item getItem() {
        return item.get();
    }

    public ItemStack getStack() {
        return new ItemStack(getItem(), 1);
    }

    public static MMUpgrades byItem(Item item) {
        for (MMUpgrades upgrade : values()) {
            if (upgrade.getItem() == item) return upgrade;
        }

        return null;
    }
}
