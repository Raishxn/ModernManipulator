package com.raishxn.modern_manipulator.common.building.providers;

import net.minecraft.world.item.ItemStack;

import com.raishxn.modern_manipulator.common.building.PortableItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * Converts stacks into item providers. Integrations can register special providers (AE cells, patterns, batteries).
 */
public class ItemProviders {

    private static final List<BiFunction<ItemStack, Boolean, IItemProvider>> FACTORIES = new ArrayList<>();

    private ItemProviders() {}

    public static void register(BiFunction<ItemStack, Boolean, IItemProvider> factory) {
        FACTORIES.add(factory);
    }

    public static @Nullable IItemProvider getProviderFor(ItemStack stack, boolean fuzzy) {
        if (stack == null || stack.isEmpty()) return null;

        for (var factory : FACTORIES) {
            IItemProvider provider = factory.apply(stack, fuzzy);
            if (provider != null) return provider;
        }

        return fuzzy ? new PortableItemStack(stack) : PortableItemStack.withNBT(stack);
    }
}
