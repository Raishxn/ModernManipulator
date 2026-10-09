package com.raishxn.modern_manipulator;

import com.raishxn.modern_manipulator.common.building.BlockCaptureDrops;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CommonEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityJoin(EntityJoinLevelEvent event) {
        BlockCaptureDrops.onEntityJoin(event);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ItemMatterManipulator.stopBuildable(event.getEntity());
    }

    @SubscribeEvent
    public void onPlayerKilled(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemMatterManipulator.stopBuildable(player);
        }
    }
}
