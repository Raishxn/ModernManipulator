package com.raishxn.modern_manipulator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;
import com.raishxn.modern_manipulator.common.network.MMNetwork;
import com.raishxn.modern_manipulator.common.network.ManipulatorConfigPacket;
import com.raishxn.modern_manipulator.common.network.ManipulatorConfigPacket.Action;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MMClientEvents {

    private MMClientEvents() {}

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || selectedManipulator(minecraft.player).isEmpty()) {
            return;
        }

        sendWhilePressed(MMKeyMappings.NEXT_MODE, Action.NEXT_MODE);
        sendWhilePressed(MMKeyMappings.PREVIOUS_MODE, Action.PREVIOUS_MODE);
        sendWhilePressed(MMKeyMappings.NEXT_SHAPE, Action.NEXT_SHAPE);
        sendWhilePressed(MMKeyMappings.PREVIOUS_SHAPE, Action.PREVIOUS_SHAPE);
        sendWhilePressed(MMKeyMappings.CLEAR_COORDS, Action.CLEAR_COORDS);
    }

    private static void sendWhilePressed(net.minecraft.client.KeyMapping keyMapping, Action action) {
        while (keyMapping.consumeClick()) {
            MMNetwork.CHANNEL.sendToServer(new ManipulatorConfigPacket(action));
        }
    }

    private static ItemStack selectedManipulator(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof MatterManipulatorItem) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof MatterManipulatorItem) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }
}
