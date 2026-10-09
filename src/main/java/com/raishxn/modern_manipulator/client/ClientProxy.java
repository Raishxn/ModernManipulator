package com.raishxn.modern_manipulator.client;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.client.gui.ManipulatorMenus;
import com.raishxn.modern_manipulator.client.rendering.MMRenderer;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.networking.Messages;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.BiConsumer;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT)
public class ClientProxy {

    /** Set by the uplink integration so that uplink machines can update their client state. */
    public static BiConsumer<Location, Integer> uplinkStateHandler;

    private ClientProxy() {}

    public static Player getPlayer() {
        return Minecraft.getInstance().player;
    }

    public static void openRadialMenu(ItemStack stack) {
        if (stack.getItem() instanceof ItemMatterManipulator manipulator) {
            ManipulatorMenus.open(manipulator, stack);
        }
    }

    public static void setUplinkState(Location location, int state) {
        if (uplinkStateHandler != null) uplinkStateHandler.accept(location, state);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        MMRenderer.renderSelection(event);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        MMRenderer.checkPlayerStoppedBuilding(event);
    }

    /**
     * Used for detecting middle mouse button clicks (pick block).
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isPickBlock()) return;

        Player player = Minecraft.getInstance().player;

        if (player == null || !player.isAlive()) return;

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (heldItem.getItem() instanceof ItemMatterManipulator manipulator) {
            event.setCanceled(true);
            event.setSwingHand(false);

            // call onMMBPressed on the client and the server
            MMState state = ItemMatterManipulator.getState(heldItem);
            manipulator.onMMBPressed(player, heldItem, state);
            ItemMatterManipulator.setState(heldItem, state);

            Messages.MMBPressed.sendToServer();
        }
    }

    /**
     * Middle clicking an item in a gui picks it as the manipulator's block.
     */
    @SubscribeEvent
    public static void onScreenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 2) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Player player = Minecraft.getInstance().player;

        if (player == null) return;

        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (!(heldItem.getItem() instanceof ItemMatterManipulator)) return;

        Slot slot = screen.getSlotUnderMouse();

        if (slot == null || !slot.hasItem()) return;

        Messages.MMBPressedInGUI.sendToServer(new Messages.CursorStack(Screen.hasShiftDown(), slot.getItem().copyWithCount(1)));

        event.setCanceled(true);
    }

    @Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEvents {

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            MMKeyInputs.register(event);
        }
    }

    static {
        MinecraftForge.EVENT_BUS.register(MMKeyInputs.class);
    }
}
