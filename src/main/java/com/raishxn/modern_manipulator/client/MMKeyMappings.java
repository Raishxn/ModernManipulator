package com.raishxn.modern_manipulator.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.raishxn.modern_manipulator.ModernManipulator;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MMKeyMappings {

    public static final String CATEGORY = "key.categories.matter_manipulator";

    public static final KeyMapping NEXT_MODE = new KeyMapping("key.matter_manipulator.next_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, CATEGORY);
    public static final KeyMapping PREVIOUS_MODE = new KeyMapping("key.matter_manipulator.previous_mode",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping NEXT_SHAPE = new KeyMapping("key.matter_manipulator.next_shape",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    public static final KeyMapping PREVIOUS_SHAPE = new KeyMapping("key.matter_manipulator.previous_shape",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);
    public static final KeyMapping CLEAR_COORDS = new KeyMapping("key.matter_manipulator.clear_coords",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    private MMKeyMappings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(NEXT_MODE);
        event.register(PREVIOUS_MODE);
        event.register(NEXT_SHAPE);
        event.register(PREVIOUS_SHAPE);
        event.register(CLEAR_COORDS);
    }
}
