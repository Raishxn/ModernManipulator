package com.raishxn.modern_manipulator.client;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;

import net.minecraft.client.Minecraft;

import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.uplink.MMUplinkMachine;
import com.raishxn.modern_manipulator.common.uplink.UplinkState;

/**
 * Applies uplink state updates on the client. Only loaded when AE2 is present.
 */
public class UplinkClient {

    private UplinkClient() {}

    public static void setState(Location location, int state) {
        var world = Minecraft.getInstance().level;

        if (world == null || !location.isInWorld(world)) return;

        if (world.getBlockEntity(location.toPos()) instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine() instanceof MMUplinkMachine uplink) {
            UplinkState[] states = UplinkState.values();

            if (state >= 0 && state < states.length) uplink.setClientState(states[state]);
        }
    }
}
