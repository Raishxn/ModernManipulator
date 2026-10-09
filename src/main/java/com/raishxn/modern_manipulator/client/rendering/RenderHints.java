package com.raishxn.modern_manipulator.client.rendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the ghost block hints (the equivalent of the StructureLib hints the original used).
 */
public class RenderHints {

    public static final RenderHints INSTANCE = new RenderHints();

    /** The texture used for hints that represent a removal or a status marker */
    private static final ResourceLocation HINT_TEXTURE = new ResourceLocation("minecraft", "block/white_stained_glass");

    private static final float INSET = 0.125f;

    private static class Hint {

        int x, y, z;
        BlockState state;
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
        Hint hint = new Hint();

        hint.x = x;
        hint.y = y;
        hint.z = z;
        hint.state = state;
        hint.tint = tint;

        pending.add(hint);
    }

    /** Rebuilds the vertex buffer now (used by the smoke test). */
    public void forceRebuild() {
        rebuild();
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

        BufferBuilder buffer = new BufferBuilder(hints.size() * 6 * 4 * DefaultVertexFormat.POSITION_COLOR_TEX.getVertexSize());
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);

        Matrix4f identity = new Matrix4f();

        for (Hint hint : hints) {
            TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];

            if (hint.state != null && !hint.state.isAir()) {
                BakedModel model = mc.getBlockRenderer().getBlockModel(hint.state);

                for (Direction dir : Direction.values()) {
                    TextureAtlasSprite sprite = null;

                    List<BakedQuad> quads = model.getQuads(hint.state, dir, random, ModelData.EMPTY, null);

                    if (quads.isEmpty()) quads = model.getQuads(hint.state, null, random, ModelData.EMPTY, null);

                    if (!quads.isEmpty()) sprite = quads.get(0).getSprite();

                    if (sprite == null) sprite = model.getParticleIcon(ModelData.EMPTY);

                    sprites[dir.ordinal()] = sprite;
                }
            } else {
                for (int i = 0; i < 6; i++) sprites[i] = hintSprite;
            }

            float x0 = hint.x - originX + INSET, y0 = hint.y - originY + INSET, z0 = hint.z - originZ + INSET;
            float x1 = hint.x - originX + 1 - INSET, y1 = hint.y - originY + 1 - INSET, z1 = hint.z - originZ + 1 - INSET;

            int a = (hint.tint >> 24) & 0xFF;
            int r = (hint.tint >> 16) & 0xFF;
            int g = (hint.tint >> 8) & 0xFF;
            int b = hint.tint & 0xFF;

            face(buffer, identity, sprites[Direction.DOWN.ordinal()], x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
            face(buffer, identity, sprites[Direction.UP.ordinal()], x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, r, g, b, a);
            face(buffer, identity, sprites[Direction.NORTH.ordinal()], x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, r, g, b, a);
            face(buffer, identity, sprites[Direction.SOUTH.ordinal()], x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, r, g, b, a);
            face(buffer, identity, sprites[Direction.WEST.ordinal()], x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, r, g, b, a);
            face(buffer, identity, sprites[Direction.EAST.ordinal()], x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, r, g, b, a);
        }

        vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vbo.bind();
        vbo.upload(buffer.end());
        VertexBuffer.unbind();
    }

    private static void face(BufferBuilder buffer, Matrix4f m, TextureAtlasSprite sprite, float ax, float ay, float az, float bx,
                             float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, int r, int g, int b, int a) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

        buffer.vertex(m, ax, ay, az).color(r, g, b, a).uv(u0, v0).endVertex();
        buffer.vertex(m, bx, by, bz).color(r, g, b, a).uv(u0, v1).endVertex();
        buffer.vertex(m, cx, cy, cz).color(r, g, b, a).uv(u1, v1).endVertex();
        buffer.vertex(m, dx, dy, dz).color(r, g, b, a).uv(u1, v0).endVertex();
    }

    public void render(PoseStack pose, Matrix4f projection, Vec3 camera) {
        if (dirty) rebuild();

        if (vbo == null) return;

        pose.pushPose();
        pose.translate(originX - camera.x, originY - camera.y, originZ - camera.z);

        RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        if (depthTest) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }

        ShaderInstance shader = GameRenderer.getPositionColorTexShader();

        vbo.bind();
        vbo.drawWithShader(pose.last().pose(), projection, shader);
        VertexBuffer.unbind();

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        pose.popPose();
    }
}
