package com.raishxn.modern_manipulator.mixin.client;

import net.minecraft.client.renderer.GameRenderer;

import com.raishxn.modern_manipulator.client.rendering.MMRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the preview after the level (and Oculus' final composite pass), before the hand. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
                     shift = At.Shift.AFTER))
    private void modern_manipulator$afterLevel(CallbackInfo ci) {
        MMRenderer.renderDeferred();
    }
}
