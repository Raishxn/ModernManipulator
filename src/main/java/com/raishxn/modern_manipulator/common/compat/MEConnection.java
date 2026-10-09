package com.raishxn.modern_manipulator.common.compat;

import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;

import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * A live connection to an ME network. Implemented by the AE2 integration.
 */
public interface MEConnection {

    /** Set by the AE2 integration */
    Holder HOLDER = new Holder();

    class Holder {

        public Function<Location, MEConnection> factory;
    }

    static @Nullable MEConnection connect(Location link) {
        return HOLDER.factory == null ? null : HOLDER.factory.apply(link);
    }

    boolean isConnected();

    /** Checks if the player is within range of an online access point and has permissions. */
    boolean canInteract(Player player);

    /** Something that identifies the storage, used to avoid extracting from the same storage twice. */
    Object getStorageIdentity();

    /**
     * Extracts items from the network.
     *
     * @param fuzzy When true, NBT is ignored
     * @return The extracted items
     */
    List<BigItemStack> extractItems(BigItemStack request, boolean fuzzy, boolean simulate, Player player);

    /**
     * Injects items into the network.
     *
     * @return The amount that couldn't be inserted
     */
    long injectItems(BigItemStack stack, Player player);

    /**
     * Injects fluids into the network.
     *
     * @return The amount that couldn't be inserted
     */
    long injectFluids(BigFluidStack stack, Player player);
}
