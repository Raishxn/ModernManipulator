package com.raishxn.modern_manipulator.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.blaze3d.platform.InputConstants;
import com.raishxn.modern_manipulator.GlobalMMConfig.InteractionConfig;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.networking.Messages;
import org.lwjgl.glfw.GLFW;

public class MMKeyInputs {

    public static final KeyMapping CONTROL = key("key.mm-ctrl", GLFW.GLFW_KEY_LEFT_CONTROL);
    public static final KeyMapping CUT = key("key.mm-cut", GLFW.GLFW_KEY_X);
    public static final KeyMapping COPY = key("key.mm-copy", GLFW.GLFW_KEY_C);
    public static final KeyMapping PASTE = key("key.mm-paste", GLFW.GLFW_KEY_V);
    public static final KeyMapping RESET = key("key.mm-reset", GLFW.GLFW_KEY_Z);

    private static KeyMapping key(String name, int code) {
        return new KeyMapping(name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, "key.mm");
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(CONTROL);
        event.register(CUT);
        event.register(COPY);
        event.register(PASTE);
        event.register(RESET);
    }

    private static void clearTransformIfNeeded() {
        if (InteractionConfig.resetTransform.get()) {
            Messages.ClearTransform.sendToServer();
            Messages.ResetArray.sendToServer();
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(InputEvent.Key event) {
        Player player = Minecraft.getInstance().player;

        if (player == null || Minecraft.getInstance().screen != null) return;

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (!(held.getItem() instanceof ItemMatterManipulator)) {
            // drain clicks so they aren't remembered
            while (CUT.consumeClick() || COPY.consumeClick() || PASTE.consumeClick() || RESET.consumeClick());
            return;
        }

        MMState state = ItemMatterManipulator.getState(held);

        if (!CONTROL.isUnbound() && !CONTROL.isDown()) {
            while (CUT.consumeClick() || COPY.consumeClick() || PASTE.consumeClick() || RESET.consumeClick());
            return;
        }

        if (CUT.consumeClick()) {
            if (state.config.placeMode != PlaceMode.MOVING) {
                Messages.SetPlaceMode.sendToServer(PlaceMode.MOVING);
            }

            if (InteractionConfig.pasteAutoClear.get()) {
                Messages.ClearCoords.sendToServer();
                clearTransformIfNeeded();
            }

            Messages.MarkCut.sendToServer();
            return;
        }

        if (COPY.consumeClick()) {
            if (state.config.placeMode != PlaceMode.COPYING) {
                Messages.SetPlaceMode.sendToServer(PlaceMode.COPYING);
            }

            if (InteractionConfig.pasteAutoClear.get()) {
                Messages.ClearCoords.sendToServer();
                clearTransformIfNeeded();
            }

            Messages.MarkCopy.sendToServer();
            return;
        }

        if (PASTE.consumeClick()) {
            // set the mode to copying if we aren't in a mode supports pasting (moving/copying)
            if (state.config.placeMode != PlaceMode.COPYING && state.config.placeMode != PlaceMode.MOVING) {
                Messages.SetPlaceMode.sendToServer(PlaceMode.COPYING);
            }

            Messages.MarkPaste.sendToServer();
            return;
        }

        if (RESET.consumeClick()) {
            Messages.ClearCoords.sendToServer();
            clearTransformIfNeeded();
        }
    }
}
