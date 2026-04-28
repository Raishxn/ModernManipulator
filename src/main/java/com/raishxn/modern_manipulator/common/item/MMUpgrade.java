package com.raishxn.modern_manipulator.common.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem.ManipulatorTier;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public enum MMUpgrade {

    POWER_P2P("power_p2p", 0, () -> MMItems.ENERGY_TUNNEL_UPGRADE.get(), MMCapability.CONNECTS_TO_UPLINK),
    MINING("mining", 1, () -> MMItems.EXCAVATION_UPGRADE.get(), MMCapability.ALLOW_REMOVING),
    SPEED("speed", 2, () -> MMItems.AUXILIARY_TELEPORTER_UPGRADE.get()),
    POWER_EFFICIENCY("power_efficiency", 3, () -> MMItems.ADAPTIVE_WIRING_HARNESS_UPGRADE.get());

    private final String serializedName;
    private final int bit;
    private final Supplier<Item> item;
    private final Set<MMCapability> providedCapabilities;

    MMUpgrade(String serializedName, int bit, Supplier<Item> item, MMCapability... providedCapabilities) {
        this.serializedName = serializedName;
        this.bit = bit;
        this.item = item;
        this.providedCapabilities = providedCapabilities.length == 0 ? Set.of() :
                EnumSet.copyOf(Set.of(providedCapabilities));
    }

    public int mask() {
        return 1 << bit;
    }

    public Item item() {
        return item.get();
    }

    public Set<MMCapability> providedCapabilities() {
        return providedCapabilities;
    }

    public boolean isAllowedOn(ManipulatorTier tier) {
        return tier.allowedUpgrades().contains(this);
    }

    public Component allowedTiersText() {
        List<Component> tiers = List.of(ManipulatorTier.values()).stream()
                .filter(this::isAllowedOn)
                .map(ManipulatorTier::displayName)
                .toList();

        Component result = Component.empty();
        for (int i = 0; i < tiers.size(); i++) {
            if (i > 0) {
                result = result.copy().append(Component.literal(", "));
            }
            result = result.copy().append(tiers.get(i));
        }
        return result;
    }

    public Component displayName() {
        return Component.translatable("matter_manipulator.upgrade." + serializedName);
    }

    public static MMUpgrade byItem(Item item) {
        for (MMUpgrade upgrade : values()) {
            if (upgrade.item() == item) {
                return upgrade;
            }
        }
        return null;
    }
}
