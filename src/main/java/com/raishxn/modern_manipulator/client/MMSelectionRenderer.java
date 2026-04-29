package com.raishxn.modern_manipulator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.BlockMovers;
import com.raishxn.modern_manipulator.common.config.MMConfig;
import com.raishxn.modern_manipulator.common.item.MMSelection;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.BlockSelectMode;
import com.raishxn.modern_manipulator.common.item.MMState.Blueprint;
import com.raishxn.modern_manipulator.common.item.MMState.BlueprintBlock;
import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import com.raishxn.modern_manipulator.common.item.MMState.PendingAction;
import com.raishxn.modern_manipulator.common.item.MMState.Shape;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MMSelectionRenderer {

    private static final long MAX_DETAILED_SHAPE_SCAN = 4096L;
    private static final int RULER_LENGTH = 128;

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
        renderExchangeTargets(player, state, cameraPosition, poseStack, lineBuffer);
        renderCableTargets(player, state, cameraPosition, poseStack, lineBuffer);
        renderBlueprintPaste(player, state, cameraPosition, poseStack, lineBuffer);
        renderPendingAction(player, state, cameraPosition, poseStack, lineBuffer);
        renderLiveSelection(minecraft, player, state, cameraPosition, poseStack, lineBuffer);
        renderCoordinateMarkers(player, state, cameraPosition, poseStack, lineBuffer);
        RenderSystem.enableDepthTest();
        bufferSource.endBatch(RenderType.lines());
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui) {
            return;
        }

        ItemStack stack = selectedManipulator(player);
        if (stack.isEmpty()) {
            return;
        }

        MMState state = MatterManipulatorItem.getState(stack);
        GuiGraphics graphics = event.getGuiGraphics();
        int centerX = minecraft.getWindow().getGuiScaledWidth() / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() - 64;
        Component summary = Component.literal(state.mode().displayName().getString() + " / " +
                state.shape().displayName().getString() + " / " + state.blockSelectMode().displayName().getString() +
                " / " + state.removeMode().displayName().getString());
        drawCenteredHudLine(graphics, minecraft, summary, centerX, y, 0xFFE6E6E6);
        y -= 10;
        PendingAction pendingAction = state.pendingAction();
        if (pendingAction != null) {
            drawCenteredHudLine(graphics, minecraft, pendingAction.progressText(), centerX, y, 0xFFFFD36A);
            y -= 10;
            if (pendingAction.paused()) {
                drawCenteredHudLine(graphics, minecraft, Component.translatable(
                        "hud.matter_manipulator.pending_paused"), centerX, y, 0xFFFFAA55);
                y -= 10;
            }
        }
        Blueprint blueprint = state.blueprint();
        if (blueprint != null) {
            Component blueprintText = Component.translatable("tooltip.matter_manipulator.blueprint",
                    blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), blueprint.volume());
            drawCenteredHudLine(graphics, minecraft, blueprintText, centerX, y,
                    blueprint.movesSource() ? 0xFFFFAA55 : 0xFF75D7FF);
            y -= 10;
            if (state.rotationY() != 0 || state.mirrorX() || state.mirrorY() || state.mirrorZ()) {
                drawCenteredHudLine(graphics, minecraft, Component.translatable(
                        "hud.matter_manipulator.transform", state.rotationY() * 90,
                        state.mirrorX() ? "X" : "-", state.mirrorY() ? "Y" : "-",
                        state.mirrorZ() ? "Z" : "-"), centerX, y, 0xFFC6FF75);
                y -= 10;
            }
            if (state.pasteArrayCopies() > 1) {
                drawCenteredHudLine(graphics, minecraft, Component.translatable(
                        "hud.matter_manipulator.array",
                        state.pasteArrayX(), state.pasteArrayY(), state.pasteArrayZ()), centerX, y, 0xFF75FFC1);
                y -= 10;
            }
            if (state.hasPasteOffset()) {
                drawCenteredHudLine(graphics, minecraft, Component.translatable(
                        "hud.matter_manipulator.offset",
                        state.pasteOffsetX(), state.pasteOffsetY(), state.pasteOffsetZ()), centerX, y, 0xFF75FFC1);
                y -= 10;
            }
        }
        if (state.mode() == MMState.ToolMode.EXCHANGING) {
            if (!state.exchangeReplacement().isEmpty()) {
                drawCenteredHudLine(graphics, minecraft, Component.translatable(
                        "hud.matter_manipulator.exchange_replacement",
                        state.exchangeReplacement().getHoverName()), centerX, y, 0xFFC6FF75);
                y -= 10;
            }
            if (state.hasExchangeWhitelist()) {
                drawCenteredHudLine(graphics, minecraft, Component.translatable(
                        "hud.matter_manipulator.exchange_whitelist",
                        state.exchangeWhitelist().size()), centerX, y, 0xFFC6FF75);
            }
        }
        if (state.mode() == MMState.ToolMode.CABLES && !state.cableStack().isEmpty()) {
            drawCenteredHudLine(graphics, minecraft, Component.translatable(
                    "hud.matter_manipulator.cable",
                    state.cableStack().getHoverName()), centerX, y, 0xFF75FFC1);
        }
        drawDimensionHud(graphics, minecraft, player, state);
    }

    private static void renderFinalSelection(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                             VertexConsumer lineBuffer) {
        MMSelection selection = state.selection();
        if (selection == null || !selection.dimension().equals(player.level().dimension().location())) {
            return;
        }

        renderSelectionShape(selection, state.blockSelectMode(), cameraPosition, poseStack, lineBuffer,
                0.2F, 0.85F, 1.0F, 0.95F);
        if (renderSelectionRulers()) {
            renderSelectionRulers(selection.min(), cameraPosition, poseStack, lineBuffer,
                    0.15F, 0.6F, 0.75F, 0.75F);
        }
        if (renderSelectionRulers() && !selection.min().equals(selection.max())) {
            renderSelectionRulers(selection.max(), cameraPosition, poseStack, lineBuffer,
                    0.15F, 0.6F, 0.75F, 0.55F);
        }
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
            if (renderSelectionRulers()) {
                renderSelectionRulers(lookingAt, cameraPosition, poseStack, lineBuffer,
                        0.75F, 0.5F, 0.15F, 0.75F);
            }
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
                    state.blockSelectMode(), cameraPosition, poseStack, lineBuffer,
                    0.15F, 0.6F, 0.75F, 0.75F);
            if (renderSelectionRulers()) {
                renderSelectionRulers(lookingAt, cameraPosition, poseStack, lineBuffer,
                        0.15F, 0.6F, 0.75F, 0.75F);
            }
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

        BlockPos min = state.pasteOrigin(coordC.pos());
        BlockPos max = min.offset(state.pasteArraySizeX(blueprint) - 1, state.pasteArraySizeY(blueprint) - 1,
                state.pasteArraySizeZ(blueprint) - 1);
        renderBlockRange(min, max, cameraPosition, poseStack, lineBuffer,
                1.0F, 0.55F, 0.15F, 0.95F);
        renderBlueprintBlockHints(player, state, blueprint, min, cameraPosition, poseStack, lineBuffer);
        renderBlockRange(min, min, cameraPosition, poseStack, lineBuffer,
                1.0F, 0.9F, 0.2F, 0.95F);
        if (renderSelectionRulers()) {
            renderSelectionRulers(min, cameraPosition, poseStack, lineBuffer,
                    0.75F, 0.5F, 0.15F, 0.75F);
        }
        if (!min.equals(coordC.pos())) {
            renderBlockRange(coordC.pos(), coordC.pos(), cameraPosition, poseStack, lineBuffer,
                    0.75F, 0.5F, 0.15F, 0.55F);
        }
        if (blueprint.movesSource() && blueprint.sourceDimension().equals(player.level().dimension().location())) {
            BlockPos sourceMin = blueprint.sourceMin();
            BlockPos sourceMax = sourceMin.offset(blueprint.sizeX() - 1, blueprint.sizeY() - 1,
                    blueprint.sizeZ() - 1);
            renderBlockRange(sourceMin, sourceMax, cameraPosition, poseStack, lineBuffer,
                    1.0F, 0.2F, 0.2F, 0.8F);
            if (renderSelectionRulers()) {
                renderSelectionRulers(sourceMin, cameraPosition, poseStack, lineBuffer,
                        1.0F, 0.2F, 0.2F, 0.65F);
            }
        }
    }

    private static void renderExchangeTargets(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                              VertexConsumer lineBuffer) {
        if (state.mode() != MMState.ToolMode.EXCHANGING || state.exchangeReplacement().isEmpty()) {
            return;
        }
        MMSelection selection = state.selection();
        if (selection == null || !selection.dimension().equals(player.level().dimension().location()) ||
                selection.scanVolume() > MMConfig.MAX_RENDER_HINT_BLOCKS.get()) {
            return;
        }
        for (BlockPos pos : selection.positions()) {
            if (!selection.contains(pos, state.blockSelectMode())) {
                continue;
            }
            if (state.isExchangeWhitelisted(player.level().getBlockState(pos))) {
                renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer,
                        0.75F, 1.0F, 0.25F, 0.85F);
            }
        }
    }

    private static void renderCableTargets(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                           VertexConsumer lineBuffer) {
        if (state.mode() != MMState.ToolMode.CABLES || state.cableStack().isEmpty()) {
            return;
        }
        MMSelection selection = state.selection();
        if (selection == null || !selection.dimension().equals(player.level().dimension().location()) ||
                selection.scanVolume() > MMConfig.MAX_RENDER_HINT_BLOCKS.get()) {
            return;
        }
        for (BlockPos pos : selection.positions()) {
            if (!selection.contains(pos, state.blockSelectMode())) {
                continue;
            }
            if (player.level().getBlockState(pos).isAir()) {
                renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer,
                        0.25F, 1.0F, 0.75F, 0.65F);
            } else {
                renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer,
                        1.0F, 0.85F, 0.1F, 0.65F);
            }
        }
    }

    private static void renderBlueprintBlockHints(Player player, MMState state, Blueprint blueprint, BlockPos origin,
                                                  Vec3 cameraPosition, PoseStack poseStack,
                                                  VertexConsumer lineBuffer) {
        long totalHints = blueprint.volume() * state.pasteArrayCopies();
        if (!MMConfig.RENDER_BLUEPRINT_HINTS.get() || totalHints > MMConfig.MAX_RENDER_HINT_BLOCKS.get()) {
            return;
        }

        int strideX = state.pasteSizeX(blueprint);
        int strideY = blueprint.sizeY();
        int strideZ = state.pasteSizeZ(blueprint);
        for (int arrayY = 0; arrayY < state.pasteArrayY(); arrayY++) {
            for (int arrayZ = 0; arrayZ < state.pasteArrayZ(); arrayZ++) {
                for (int arrayX = 0; arrayX < state.pasteArrayX(); arrayX++) {
                    BlockPos arrayOrigin = origin.offset(arrayX * strideX, arrayY * strideY, arrayZ * strideZ);
                    for (BlueprintBlock sourceBlock : blueprint.blocks()) {
                        BlueprintBlock pasteBlock = state.transformedBlock(blueprint, sourceBlock);
                        BlockPos pos = arrayOrigin.offset(pasteBlock.x(), pasteBlock.y(), pasteBlock.z());
                        if (!BlockMovers.canPasteBlock(player.level(), pos, pasteBlock, state.removeMode())) {
                            renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer,
                                    1.0F, 0.15F, 0.15F, 0.85F);
                        } else if (player.level().getBlockState(pos).isAir()) {
                            renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer,
                                    0.9F, 0.95F, 1.0F, 0.35F);
                        } else {
                            renderBlockRange(pos, pos, cameraPosition, poseStack, lineBuffer,
                                    1.0F, 0.85F, 0.1F, 0.65F);
                        }
                    }
                }
            }
        }
    }

    private static void renderCoordinateMarkers(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                                VertexConsumer lineBuffer) {
        renderMarkedPosition(player, state.coordA(), cameraPosition, poseStack, lineBuffer,
                0.2F, 1.0F, 0.35F, 0.95F);
        renderMarkedPosition(player, state.coordB(), cameraPosition, poseStack, lineBuffer,
                0.2F, 0.85F, 1.0F, 0.95F);
        renderMarkedPosition(player, state.coordC(), cameraPosition, poseStack, lineBuffer,
                1.0F, 0.75F, 0.2F, 0.95F);
    }

    private static void renderMarkedPosition(Player player, MarkedPosition markedPosition, Vec3 cameraPosition,
                                             PoseStack poseStack, VertexConsumer lineBuffer,
                                             float red, float green, float blue, float alpha) {
        if (markedPosition == null || !markedPosition.dimension().equals(player.level().dimension().location())) {
            return;
        }
        renderBlockRange(markedPosition.pos(), markedPosition.pos(), cameraPosition, poseStack, lineBuffer,
                red, green, blue, alpha);
    }

    private static void renderPendingAction(Player player, MMState state, Vec3 cameraPosition, PoseStack poseStack,
                                            VertexConsumer lineBuffer) {
        PendingAction action = state.pendingAction();
        if (action == null || !action.selection().dimension().equals(player.level().dimension().location())) {
            return;
        }

        renderSelectionShape(action.selection(), action.blockSelectMode(), cameraPosition, poseStack, lineBuffer,
                1.0F, 0.9F, 0.2F, 0.75F);
        for (BlockPos warningPos : action.warnings()) {
            renderBlockRange(warningPos, warningPos, cameraPosition, poseStack, lineBuffer,
                    1.0F, 0.85F, 0.1F, 0.95F);
        }
        for (BlockPos errorPos : action.errors()) {
            renderBlockRange(errorPos, errorPos, cameraPosition, poseStack, lineBuffer,
                    1.0F, 0.15F, 0.15F, 0.95F);
        }
        if (!action.isComplete()) {
            BlockPos cursorPos = action.positionAt(action.cursor());
            if (action.selection().contains(cursorPos, action.blockSelectMode())) {
                renderBlockRange(cursorPos, cursorPos, cameraPosition, poseStack, lineBuffer,
                        1.0F, 1.0F, 1.0F, 0.95F);
            }
        }
    }

    private static void renderSelectionShape(MMSelection selection, Vec3 cameraPosition, PoseStack poseStack,
                                             VertexConsumer lineBuffer, float red, float green, float blue,
                                             float alpha) {
        renderSelectionShape(selection, BlockSelectMode.ALL, cameraPosition, poseStack, lineBuffer,
                red, green, blue, alpha);
    }

    private static void renderSelectionShape(MMSelection selection, BlockSelectMode blockSelectMode,
                                             Vec3 cameraPosition, PoseStack poseStack,
                                             VertexConsumer lineBuffer, float red, float green, float blue,
                                             float alpha) {
        if ((selection.shape() == Shape.CUBE && blockSelectMode == BlockSelectMode.ALL) ||
                selection.scanVolume() > MAX_DETAILED_SHAPE_SCAN) {
            renderBlockRange(selection.min(), selection.max(), cameraPosition, poseStack, lineBuffer,
                    red, green, blue, alpha);
            return;
        }

        for (BlockPos pos : selection.positions()) {
            if (selection.contains(pos, blockSelectMode)) {
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
        if (MMConfig.RENDER_FILLED_BOXES.get()) {
            renderFilledBox(poseStack, bounds.inflate(0.01D), red, green, blue, alpha * 0.25F);
        }
        LevelRenderer.renderLineBox(poseStack, lineBuffer, bounds, red, green, blue, alpha);
    }

    private static boolean renderSelectionRulers() {
        return MMConfig.RENDER_SELECTION_RULERS.get();
    }

    private static void renderFilledBox(PoseStack poseStack, AABB bounds, float red, float green, float blue,
                                        float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = poseStack.last().pose();
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        double minX = bounds.minX;
        double minY = bounds.minY;
        double minZ = bounds.minZ;
        double maxX = bounds.maxX;
        double maxY = bounds.maxY;
        double maxZ = bounds.maxZ;

        filledVertex(buffer, matrix, minX, minY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, minY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, maxY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, maxY, minZ, red, green, blue, alpha);

        filledVertex(buffer, matrix, maxX, minY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, minY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, maxY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, maxY, maxZ, red, green, blue, alpha);

        filledVertex(buffer, matrix, minX, minY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, minY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, maxY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, maxY, maxZ, red, green, blue, alpha);

        filledVertex(buffer, matrix, maxX, minY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, minY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, maxY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, maxY, minZ, red, green, blue, alpha);

        filledVertex(buffer, matrix, minX, maxY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, maxY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, maxY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, maxY, maxZ, red, green, blue, alpha);

        filledVertex(buffer, matrix, minX, minY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, minY, maxZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, maxX, minY, minZ, red, green, blue, alpha);
        filledVertex(buffer, matrix, minX, minY, minZ, red, green, blue, alpha);

        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void filledVertex(BufferBuilder buffer, Matrix4f matrix, double x, double y, double z,
                                     float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    private static void renderSelectionRulers(BlockPos origin, Vec3 cameraPosition, PoseStack poseStack,
                                              VertexConsumer lineBuffer, float red, float green, float blue,
                                              float alpha) {
        double x = origin.getX() + 0.5D - cameraPosition.x;
        double y = origin.getY() + 0.5D - cameraPosition.y;
        double z = origin.getZ() + 0.5D - cameraPosition.z;
        renderLine(poseStack, lineBuffer, x, y, z, x + RULER_LENGTH, y, z, red, green, blue, alpha);
        renderLine(poseStack, lineBuffer, x, y, z, x - RULER_LENGTH, y, z, red, green, blue, alpha);
        renderLine(poseStack, lineBuffer, x, y, z, x, y + RULER_LENGTH, z, red, green, blue, alpha);
        renderLine(poseStack, lineBuffer, x, y, z, x, y - RULER_LENGTH, z, red, green, blue, alpha);
        renderLine(poseStack, lineBuffer, x, y, z, x, y, z + RULER_LENGTH, red, green, blue, alpha);
        renderLine(poseStack, lineBuffer, x, y, z, x, y, z - RULER_LENGTH, red, green, blue, alpha);
    }

    private static void renderLine(PoseStack poseStack, VertexConsumer lineBuffer,
                                   double x1, double y1, double z1, double x2, double y2, double z2,
                                   float red, float green, float blue, float alpha) {
        float normalX = (float) (x2 - x1);
        float normalY = (float) (y2 - y1);
        float normalZ = (float) (z2 - z1);
        float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        if (length <= 0.0F) {
            return;
        }
        normalX /= length;
        normalY /= length;
        normalZ /= length;
        PoseStack.Pose pose = poseStack.last();
        lineBuffer.vertex(pose.pose(), (float) x1, (float) y1, (float) z1)
                .color(red, green, blue, alpha)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
        lineBuffer.vertex(pose.pose(), (float) x2, (float) y2, (float) z2)
                .color(red, green, blue, alpha)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
    }

    private static void drawCenteredHudLine(GuiGraphics graphics, Minecraft minecraft, Component text, int centerX,
                                            int y, int color) {
        int width = minecraft.font.width(text);
        graphics.fill(centerX - width / 2 - 3, y - 1, centerX + width / 2 + 3, y + 9, 0x90000000);
        graphics.drawString(minecraft.font, text, centerX - width / 2, y, color);
    }

    private static void drawDimensionHud(GuiGraphics graphics, Minecraft minecraft, Player player, MMState state) {
        BlockPos min = null;
        BlockPos max = null;
        MMSelection selection = state.selection();
        if (selection != null && selection.dimension().equals(player.level().dimension().location())) {
            min = selection.min();
            max = selection.max();
        } else if (minecraft.hitResult instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() != HitResult.Type.BLOCK) {
                return;
            }
            MarkedPosition coordA = state.coordA();
            if (coordA != null && coordA.dimension().equals(player.level().dimension().location())) {
                BlockPos lookingAt = blockHitResult.getBlockPos();
                min = new BlockPos(Math.min(coordA.pos().getX(), lookingAt.getX()),
                        Math.min(coordA.pos().getY(), lookingAt.getY()),
                        Math.min(coordA.pos().getZ(), lookingAt.getZ()));
                max = new BlockPos(Math.max(coordA.pos().getX(), lookingAt.getX()),
                        Math.max(coordA.pos().getY(), lookingAt.getY()),
                        Math.max(coordA.pos().getZ(), lookingAt.getZ()));
            }
        }
        if (min == null || max == null) {
            return;
        }

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;
        long volume = (long) sizeX * sizeY * sizeZ;
        Component text = Component.literal("dX=" + sizeX + " dY=" + sizeY + " dZ=" + sizeZ + " V=" + volume);
        int x = minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(text) - 18;
        int y = minecraft.getWindow().getGuiScaledHeight() - 42;
        graphics.drawString(minecraft.font, text, x + 1, y + 1, 0xAAFFFFFF, false);
        graphics.drawString(minecraft.font, text, x, y, 0xFF000000, false);
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
