package com.raishxn.modern_manipulator.client.rendering;

import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.raishxn.modern_manipulator.ModernManipulator;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MMShaders {

    public static ShaderInstance FANCYBOX;
    public static ShaderInstance GHOST;

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ModernManipulator.id("mm_fancybox"),
                DefaultVertexFormat.POSITION_COLOR_TEX), shader -> FANCYBOX = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ModernManipulator.id("mm_ghost"),
                DefaultVertexFormat.POSITION_COLOR_TEX), shader -> GHOST = shader);
    }
}
