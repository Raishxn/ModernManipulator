package com.raishxn.modern_manipulator.common.uplink;

import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;

import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;

import java.util.List;
import java.util.Map;

/**
 * The interface the manipulator uses to talk to a quantum uplink.
 */
public interface IUplinkMulti {

    Map<Long, IUplinkMulti> UPLINKS = new Long2ObjectOpenHashMap<>();

    static IUplinkMulti getUplink(long address) {
        IUplinkMulti uplink = UPLINKS.get(address);

        if (uplink != null && !uplink.isValid()) {
            UPLINKS.remove(address);
            return null;
        }

        return uplink;
    }

    static void registerUplink(long address, IUplinkMulti uplink) {
        UPLINKS.put(address, uplink);
    }

    static void unregisterUplink(long address, IUplinkMulti uplink) {
        UPLINKS.remove(address, uplink);
    }

    long getAddress();

    /** False when the machine was removed or unloaded. */
    boolean isValid();

    boolean isActive();

    Location getLocation();

    UplinkState getState();

    /** Something that identifies the storage, used to avoid extracting from the same storage twice. */
    Object getStorageIdentity();

    /**
     * Drains power from the uplink.
     *
     * @return The amount of EU drained
     */
    double drainPower(double requestedEU);

    ObjectObjectImmutablePair<UplinkStatus, List<BigItemStack>> tryConsumeItems(List<BigItemStack> requestedItems, boolean simulate,
        boolean fuzzy);

    UplinkStatus tryGivePlayerItems(List<BigItemStack> items);

    UplinkStatus tryGivePlayerFluids(List<BigFluidStack> fluids);

    void submitPlan(Player player, String details, List<BigItemStack> requiredItems, boolean autocraft);

    void clearManualPlans(Player player);

    void cancelAutoPlans(Player player);
}
