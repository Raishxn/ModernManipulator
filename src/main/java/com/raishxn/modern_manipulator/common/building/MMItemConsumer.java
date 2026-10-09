package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.building.consumers.DefaultItemConsumer;
import com.raishxn.modern_manipulator.common.building.consumers.IItemConsumer;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import it.unimi.dsi.fastutil.ints.IntObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;

import java.util.Comparator;

/**
 * An inventory consumer that will provide items to MM when placing blocks
 */
public class MMItemConsumer {

    private static int counter = 0;

    private static final ObjectSortedSet<IntObjectImmutablePair<IItemConsumer>> consumers = new ObjectAVLTreeSet<>(
            Comparator.comparingInt((IntObjectImmutablePair<IItemConsumer> p) -> p.leftInt())
                    .thenComparingInt(p -> p.right().hashCode()));

    /**
     * Registers a consumer to be used in MM
     *
     * @param priority The priority in which the consumer will be called (lower first)
     * @param consumer The consumer implementation
     */
    public static void registerConsumer(int priority, IItemConsumer consumer) {
        consumers.add(new IntObjectImmutablePair<>(priority, consumer));
    }

    static {
        registerConsumer(Integer.MIN_VALUE, new DefaultItemConsumer());
    }

    /**
     * Try to consume provided item from inventory
     */
    public static BigItemStack consume(IPseudoInventory inv, BigItemStack item, int flags) {
        BigItemStack out = item.copy().setStackSize(0);

        item = item.copy();

        int actualFlags = 0;
        if ((flags & IPseudoInventory.CONSUME_SIMULATED) != 0) {
            actualFlags |= IPseudoInventory.CONSUME_SIMULATED;
        }
        if ((flags & IPseudoInventory.CONSUME_IGNORE_CREATIVE) != 0) {
            actualFlags |= IPseudoInventory.CONSUME_IGNORE_CREATIVE;
        }

        for (IntObjectImmutablePair<IItemConsumer> pair : consumers) {
            IItemConsumer consumer = pair.right();
            consumer.consume(inv, item, out, actualFlags);

            if (item.getStackSize() <= 0) break;
        }

        return out.getStackSize() > 0 ? out : null;
    }

    public static BigItemStack consume(IPseudoInventory inv, BigItemStack item) {
        return consume(inv, item, 0);
    }
}
