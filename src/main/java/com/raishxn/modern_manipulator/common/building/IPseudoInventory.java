package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;

import java.util.List;

/**
 * Something that can accept and provide items/fluids.
 */
public interface IPseudoInventory {

    /** Items will not actually be consumed. */
    int CONSUME_SIMULATED = 0b1;
    /** Items will be fuzzily-matched. Ignores NBT */
    int CONSUME_FUZZY = 0b10;
    /** Not all items must be extracted. */
    int CONSUME_PARTIAL = 0b100;
    /** Creative mode infinite supply will be ignored. */
    int CONSUME_IGNORE_CREATIVE = 0b1000;
    /** Only consume real items (extractions while planning will always fail). */
    int CONSUME_REAL_ONLY = 0b10000;

    /**
     * Atomically extracts items from this pseudo inventory.
     * The returned list is guaranteed to at minimum be equal to the items param.
     * If the extraction succeeded and partial mode wasn't enabled, extraneous items will not be extracted and the
     * returned list will contain the same items as the request.
     *
     * @param items The list of items to extract.
     * @param flags The flags (see {@link IPseudoInventory#CONSUME_SIMULATED}, {@link IPseudoInventory#CONSUME_FUZZY},
     *              etc).
     * @return Key = whether the extract was successful. Value = the list of items extracted (only relevant for fuzzy
     *         mode).
     */
    BooleanObjectImmutablePair<List<BigItemStack>> tryConsumeItems(List<BigItemStack> items, int flags);

    /**
     * Consumes a set of items.
     *
     * @return True when the items were successfully consumed.
     */
    default boolean tryConsumeItems(ItemStack... items) {
        return tryConsumeItems(MMUtils.mapToList(items, BigItemStack::create), 0).leftBoolean();
    }

    void givePlayerItems(List<BigItemStack> items);

    default void givePlayerItems(ItemStack... items) {
        givePlayerItems(MMUtils.mapToList(items, BigItemStack::create));
    }

    void givePlayerFluids(List<BigFluidStack> fluids);

    default void givePlayerFluids(FluidStack... fluids) {
        givePlayerFluids(MMUtils.mapToList(fluids, BigFluidStack::create));
    }
}
