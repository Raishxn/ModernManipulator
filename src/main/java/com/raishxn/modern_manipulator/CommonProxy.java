package com.raishxn.modern_manipulator;

import com.raishxn.modern_manipulator.common.items.manipulator.Location;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import it.unimi.dsi.fastutil.longs.LongList;

/**
 * Static entry points that dispatch to the client without loading client classes on the server.
 */
public class CommonProxy {

    private CommonProxy() {}

    public static Player getClientPlayer() {
        return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> com.raishxn.modern_manipulator.client.ClientProxy.getPlayer());
    }

    public static void openRadialMenu(Player player, ItemStack stack) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.raishxn.modern_manipulator.client.ClientProxy.openRadialMenu(stack));
    }

    public static void setStatusHints(LongList errors, LongList warnings) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> com.raishxn.modern_manipulator.client.rendering.MMRenderer.setStatusHints(errors, warnings));
    }

    public static void setUplinkState(Location location, int state) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.raishxn.modern_manipulator.client.ClientProxy.setUplinkState(location, state));
    }
}
