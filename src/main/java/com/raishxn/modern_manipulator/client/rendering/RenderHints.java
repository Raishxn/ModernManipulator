package com.raishxn.modern_manipulator.client.rendering;

import com.raishxn.modern_manipulator.ModernManipulator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the ghost block hints (the equivalent of the StructureLib hints the original used).
 * Blocks are drawn with their real model, cables/pipes/ae parts with their item model, and removals/status markers as
 * tinted glass cubes. Everything is baked into a vertex buffer that is only rebuilt when the hints change.
 */
public class RenderHints {

    public static final RenderHints INSTANCE = new RenderHints();

    /** The texture used for hints that represent a removal or a status marker */
    private static final ResourceLocation HINT_TEXTURE = new ResourceLocation("minecraft", "block/white_stained_glass");

    /** Ghost blocks are slightly smaller than a full block so that they don't z-fight with the terrain. */
    private static final float SCALE = 0.7f;
    private static final int GHOST_ALPHA = 0x80;

    private static class Hint {

        int x, y, z;
        BlockState state;
        ItemStack stack;
        int tint;
    }

    private List<Hint> pending = new ArrayList<>();
    private List<Hint> hints = new ArrayList<>();
    private boolean depthTest = false;
    private boolean dirty = false;

    private VertexBuffer vbo;
    private int originX, originY, originZ;

    public void start() {
        pending = new ArrayList<>();
    }

    public void setDepthTest(boolean depthTest) {
        this.depthTest = depthTest;
    }

    public void finish() {
        hints = pending;
        pending = new ArrayList<>();
        dirty = true;
    }

    public void reset() {
        pending = new ArrayList<>();
        hints = new ArrayList<>();
        dirty = true;
    }

    /**
     * Adds a hint.
     *
     * @param state The block to show, or null to show the generic hint texture
     * @param tint  ARGB tint
     */
    public void addHint(int x, int y, int z, BlockState state, int tint) {
        addHint(x, y, z, state, null, tint);
    }

    /**
     * Adds a hint.
     *
     * @param state The block to show, or null to show the generic hint texture
     * @param stack An item to show instead of the block (cables, parts), or null
     * @param tint  ARGB tint
     */
    public void addHint(int x, int y, int z, BlockState state, ItemStack stack, int tint) {
        Hint hint = new Hint();

        hint.x = x;
        hint.y = y;
        hint.z = z;
        hint.state = state;
        hint.stack = stack;
        hint.tint = tint;

        pending.add(hint);
    }

    /** Rebuilds the vertex buffer now (used by the smoke test). */
    public void forceRebuild() {
        rebuild();
    }

    /**
     * A vertex consumer that writes POSITION_COLOR_TEX vertices with a tint, dropping every other attribute.
     */
    private static class GhostConsumer implements VertexConsumer {

        private final BufferBuilder buffer;
        private float tr = 1, tg = 1, tb = 1, ta = 1;

        GhostConsumer(BufferBuilder buffer) {
            this.buffer = buffer;
        }

        void setTint(int argb, int alpha) {
            tr = ((argb >> 16) & 0xFF) / 255f;
            tg = ((argb >> 8) & 0xFF) / 255f;
            tb = (argb & 0xFF) / 255f;
            ta = alpha / 255f;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            // fake a bit of directional shading so that the shape stays readable without lighting
            float shade = 0.75f + 0.25f * Math.abs(ny) + 0.1f * Math.abs(nz);
            shade = Math.min(1f, shade);

            buffer.vertex(x, y, z).color(r * tr * shade, g * tg * shade, b * tb * shade, a * ta).uv(u, v).endVertex();
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            buffer.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            buffer.color((int) (r * tr), (int) (g * tg), (int) (b * tb), (int) (a * ta));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            buffer.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            buffer.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}
    }

    private void rebuild() {
        dirty = false;

        if (vbo != null) {
            vbo.close();
            vbo = null;
        }

        if (hints.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        TextureAtlas atlas = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        TextureAtlasSprite hintSprite = atlas.getSprite(HINT_TEXTURE);

        Hint first = hints.get(0);
        originX = first.x;
        originY = first.y;
        originZ = first.z;

        RandomSource random = RandomSource.create(42);

        BufferBuilder buffer = new BufferBuilder(Math.max(256, hints.size() * 24 * DefaultVertexFormat.POSITION_COLOR_TEX.getVertexSize()));
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        GhostConsumer consumer = new GhostConsumer(buffer);
        PoseStack pose = new PoseStack();

        for (Hint hint : hints) {
            pose.pushPose();
            pose.translate(hint.x - originX + 0.5, hint.y - originY + 0.5, hint.z - originZ + 0.5);
            pose.scale(SCALE, SCALE, SCALE);

            try {
                if (hint.stack != null && !hint.stack.isEmpty()) {
                    consumer.setTint(hint.tint, GHOST_ALPHA);

                    pose.scale(1.6f, 1.6f, 1.6f);

                    mc.getItemRenderer()
                        .renderStatic(
                            hint.stack,
                            ItemDisplayContext.FIXED,
                            LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,
                            pose,
                            renderType -> consumer,
                            mc.level,
                            0);
                } else if (hint.state != null && !hint.state.isAir() && hint.state.getRenderShape() == RenderShape.MODEL) {
                    consumer.setTint(hint.tint, GHOST_ALPHA);

                    pose.translate(-0.5, -0.5, -0.5);

                    BakedModel model = mc.getBlockRenderer().getBlockModel(hint.state);

                    for (RenderType renderType : model.getRenderTypes(hint.state, random, ModelData.EMPTY)) {
                        mc.getBlockRenderer()
                            .getModelRenderer()
                            .renderModel(
                                pose.last(),
                                consumer,
                                hint.state,
                                model,
                                1f,
                                1f,
                                1f,
                                LightTexture.FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY,
                                ModelData.EMPTY,
                                renderType);
                    }
                } else {
                    // removal or status marker
                    consumer.setTint(hint.tint, 0x80);

                    pose.translate(-0.5, -0.5, -0.5);

                    cube(consumer, pose.last().pose(), hintSprite);
                }
            } catch (Throwable t) {
                ModernManipulator.LOG.debug("Could not draw hint for {}", hint.state, t);
            }

            pose.popPose();
        }

        BufferBuilder.RenderedBuffer rendered = buffer.end();

        vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(rendered);
        VertexBuffer.unbind();
    }

    private static void cube(GhostConsumer consumer, Matrix4f m, TextureAtlasSprite sprite) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

        float[][] faces = {
            { 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1 },
            { 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0 },
            { 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0 },
            { 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1 },
            { 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1 },
            { 1, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0 },
        };

        float[][] uvs = { { u0, v0 }, { u0, v1 }, { u1, v1 }, { u1, v0 } };

        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                var p = m.transformPosition(f[i * 3], f[i * 3 + 1], f[i * 3 + 2], new org.joml.Vector3f());

                consumer.vertex(p.x, p.y, p.z, 1, 1, 1, 1, uvs[i][0], uvs[i][1], 0, 0, 0, 1, 0);
            }
        }
    }

    public void render(PoseStack pose, Matrix4f projection, Vec3 camera) {
        if (dirty) rebuild();

        if (vbo == null) return;

        pose.pushPose();
        pose.translate(originX - camera.x, originY - camera.y, originZ - camera.z);

        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }

        vbo.bind();
        vbo.drawWithShader(pose.last().pose(), projection, MMShaders.GHOST != null ? MMShaders.GHOST : GameRenderer.getPositionColorTexShader());
        VertexBuffer.unbind();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        pose.popPose();
    }
}
