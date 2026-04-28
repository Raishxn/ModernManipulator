package com.raishxn.modern_manipulator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.item.MMSelection;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.Blueprint;
import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import com.raishxn.modern_manipulator.common.item.MMState.Shape;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MMSelectionRenderer {

    private static final long MAX_DETAILED_SHAPE_SCAN = 4096L;

    private MMSelectionRenderer() {}

    @SubscribeEvent
    public static void renderSelection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }

        ItemStack stack = selectedManipulator(player);
        if (stack.isEmpty()) {
            return;
        }

        MMState state = MatterManipulatorItem.getState(stack);
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());

        RenderSystem.disableDepthTest();
        RenderSystem.lineWidth(2.0F);
        renderFinalSelection(player, state, cameraPosition, poseStack, lineBuffer);
        renderBlueprintPaste(player, state, cameraPosition, poseStack, lineBuffer);
        renderLiveSelection(minecraft, player, state, cameraPosition, poseStack, lineBuffer);
        RenderSystem.enableDepthTest();
        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderFinalSelection(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                             VertexConsumer lineBuffer) {
        MMSelection selection = state.selection();
        if (selection == null || !selection.dimension().equals(player.level().dimension().location())) {
            return;
        }

        renderSelectionShape(selection, cameraPosition, poseStack, lineBuffer,
                0.2F, 0.85F, 1.0F, 0.95F);
    }

    private static void renderLiveSelection(Minecraft minecraft, Player player, MMState state, Vec3 cameraPosition,
                                            PoseStack poseStack, VertexConsumer lineBuffer) {
        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult) ||
                blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos lookingAt = blockHitResult.getBlockPos();
        MarkedPosition coordA = state.coordA();
        MarkedPosition coordB = state.coordB();
        if (coordA == null) {
            renderBlockRange(lookingAt, lookingAt, cameraPosition, poseStack, lineBuffer,
                    0.75F, 0.5F, 0.15F, 0.85F);
            return;
        }
        if (!coordA.dimension().equals(player.level().dimension().location())) {
            return;
        }

        if (coordB == null || player.isShiftKeyDown()) {
            BlockPos min = new BlockPos(Math.min(coordA.pos().getX(), lookingAt.getX()),
                    Math.min(coordA.pos().getY(), lookingAt.getY()),
                    Math.min(coordA.pos().getZ(), lookingAt.getZ()));
            BlockPos max = new BlockPos(Math.max(coordA.pos().getX(), lookingAt.getX()),
                    Math.max(coordA.pos().getY(), lookingAt.getY()),
                    Math.max(coordA.pos().getZ(), lookingAt.getZ()));
            renderSelectionShape(new MMSelection(player.level().dimension().location(), min, max, state.shape()),
                    cameraPosition, poseStack, lineBuffer,
                    0.15F, 0.6F, 0.75F, 0.75F);
        }

        renderBlockRange(coordA.pos(), coordA.pos(), cameraPosition, poseStack, lineBuffer,
                0.2F, 1.0F, 0.35F, 0.95F);
    }

    private static void renderBlueprintPaste(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                             VertexConsumer lineBuffer) {
        Blueprint blueprint = state.blueprint();
        MarkedPosition coordC = state.coordC();
        if (blueprint == null || coordC == null ||
                !coordC.dimension().equals(player.level().dimension().location())) {
            return;
        }

        BlockPos min = coordC.pos();
        BlockPos max = min.offset(blueprint.sizeX() - 1, blueprint.sizeY() - 1, blueprint.sizeZ() - 1);
        renderBlockRange(min, max, cameraPosition, poseStack, lineBuffer,
                1.0F, 0.55F, 0.15F, 0.95F);
        renderBlockRange(min, min, cameraPosition, poseStack, lineBuffer,
                1.0F, 0.9F, 0.2F, 0.95F);
    }

    private static void renderSelectionShape(MMSelection selection, Vec3 cameraPosition, PoseStack poseStack,
                                             VertexConsumer lineBuffer, float red, float green, float blue,
                                             float alpha) {
        if (selection.shape() == Shape.CUBE || selection.scanVolume() > MAX_DETAILED_SHAPE_SCAN) {
            renderBlockRange(selection.min(), selection.max(), cameraPosition, poseStack, lineBuffer,
                    red, green, blue, alpha);
            return;
        }

        for (BlockPos pos : selection.positions()) {
            if (selection.contains(pos)) {
                renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer, red, green, blue, alpha);
            }
        }
    }

    private static void renderBlockRange(BlockPos min, BlockPos max, Vec3 cameraPosition, PoseStack poseStack,
                                         VertexConsumer lineBuffer, float red, float green, float blue, float alpha) {
        AABB bounds = new AABB(min, max.offset(1, 1, 1)).move(
                -cameraPosition.x,
                -cameraPosition.y,
                -cameraPosition.z);
        LevelRenderer.renderLineBox(poseStack, lineBuffer, bounds, red, green, blue, alpha);
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
