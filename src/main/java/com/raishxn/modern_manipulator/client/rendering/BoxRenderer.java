package com.raishxn.modern_manipulator.client.rendering;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws the fancy region boxes (animated stripes, port of the original fancybox shader) and rulers.
 */
public class BoxRenderer {

    public static final BoxRenderer INSTANCE = new BoxRenderer();

    private static final int RULER_LENGTH = 128;

    private PoseStack pose;
    private Vec3 camera;
    private BufferBuilder boxes;

    /**
     * Starts rendering fancy boxes. Should only be called once per frame, to allow quad sorting.
     */
    public void start(PoseStack pose, Vec3 camera) {
        this.pose = pose;
        this.camera = camera;
        this.boxes = null;
    }

    /**
     * Draws a fancy box around an AABB.
     */
    public void drawAround(AABB aabb, Vector3f colour) {
        if (boxes == null) {
            boxes = Tesselator.getInstance().getBuilder();
            boxes.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        }

        aabb = aabb.inflate(0.01);

        Matrix4f m = pose.last().pose();

        float ox = (float) (aabb.minX - camera.x), oy = (float) (aabb.minY - camera.y), oz = (float) (aabb.minZ - camera.z);
        float dX = (float) aabb.getXsize(), dY = (float) aabb.getYsize(), dZ = (float) aabb.getZsize();

        float r = colour.x, g = colour.y, b = colour.z, a = 0.25f;

        // spotless:off
        // bottom face
        v(m, ox, oy, oz, 0, 0, 0, 0, r, g, b, a); v(m, ox, oy, oz, dX, 0, 0, dX, 0, r, g, b, a); v(m, ox, oy, oz, dX, 0, dZ, dX, dZ, r, g, b, a); v(m, ox, oy, oz, 0, 0, dZ, 0, dZ, r, g, b, a);
        v(m, ox, oy, oz, 0, 0, 0, 0, r, g, b, a); v(m, ox, oy, oz, 0, 0, dZ, 0, dZ, r, g, b, a); v(m, ox, oy, oz, dX, 0, dZ, dX, dZ, r, g, b, a); v(m, ox, oy, oz, dX, 0, 0, dX, 0, r, g, b, a);
        // top face
        v(m, ox, oy, oz, 0, dY, 0, dY, 0, r, g, b, a); v(m, ox, oy, oz, 0, dY, dZ, dY, dZ, r, g, b, a); v(m, ox, oy, oz, dX, dY, dZ, dY + dX, dZ, r, g, b, a); v(m, ox, oy, oz, dX, dY, 0, dY + dX, 0, r, g, b, a);
        v(m, ox, oy, oz, 0, dY, 0, dY, 0, r, g, b, a); v(m, ox, oy, oz, dX, dY, 0, dY + dX, 0, r, g, b, a); v(m, ox, oy, oz, dX, dY, dZ, dY + dX, dZ, r, g, b, a); v(m, ox, oy, oz, 0, dY, dZ, dY, dZ, r, g, b, a);
        // west face
        v(m, ox, oy, oz, 0, 0, 0, 0, 0, r, g, b, a); v(m, ox, oy, oz, 0, 0, dZ, 0, dZ, r, g, b, a); v(m, ox, oy, oz, 0, dY, dZ, dY, dZ, r, g, b, a); v(m, ox, oy, oz, 0, dY, 0, dY, 0, r, g, b, a);
        v(m, ox, oy, oz, 0, 0, 0, 0, 0, r, g, b, a); v(m, ox, oy, oz, 0, dY, 0, dY, 0, r, g, b, a); v(m, ox, oy, oz, 0, dY, dZ, dY, dZ, r, g, b, a); v(m, ox, oy, oz, 0, 0, dZ, 0, dZ, r, g, b, a);
        // east face
        v(m, ox, oy, oz, dX, dY, dZ, dX + dY, dZ, r, g, b, a); v(m, ox, oy, oz, dX, 0, dZ, dX, dZ, r, g, b, a); v(m, ox, oy, oz, dX, 0, 0, dX, 0, r, g, b, a); v(m, ox, oy, oz, dX, dY, 0, dX + dY, 0, r, g, b, a);
        v(m, ox, oy, oz, dX, dY, dZ, dX + dY, dZ, r, g, b, a); v(m, ox, oy, oz, dX, dY, 0, dX + dY, 0, r, g, b, a); v(m, ox, oy, oz, dX, 0, 0, dX, 0, r, g, b, a); v(m, ox, oy, oz, dX, 0, dZ, dX, dZ, r, g, b, a);
        // north face
        v(m, ox, oy, oz, 0, 0, 0, 0, 0, r, g, b, a); v(m, ox, oy, oz, dX, 0, 0, dX, 0, r, g, b, a); v(m, ox, oy, oz, dX, dY, 0, dX, dY, r, g, b, a); v(m, ox, oy, oz, 0, dY, 0, 0, dY, r, g, b, a);
        v(m, ox, oy, oz, 0, 0, 0, 0, 0, r, g, b, a); v(m, ox, oy, oz, 0, dY, 0, 0, dY, r, g, b, a); v(m, ox, oy, oz, dX, dY, 0, dX, dY, r, g, b, a); v(m, ox, oy, oz, dX, 0, 0, dX, 0, r, g, b, a);
        // south face
        v(m, ox, oy, oz, 0, 0, dZ, dZ, 0, r, g, b, a); v(m, ox, oy, oz, 0, dY, dZ, dZ, dY, r, g, b, a); v(m, ox, oy, oz, dX, dY, dZ, dZ + dX, dY, r, g, b, a); v(m, ox, oy, oz, dX, 0, dZ, dZ + dX, 0, r, g, b, a);
        v(m, ox, oy, oz, 0, 0, dZ, dZ, 0, r, g, b, a); v(m, ox, oy, oz, dX, 0, dZ, dZ + dX, 0, r, g, b, a); v(m, ox, oy, oz, dX, dY, dZ, dZ + dX, dY, r, g, b, a); v(m, ox, oy, oz, 0, dY, dZ, dZ, dY, r, g, b, a);
        // spotless:on
    }

    private void v(Matrix4f m, float ox, float oy, float oz, float x, float y, float z, float u, float vv, float r, float g, float b,
                   float a) {
        boxes.vertex(m, ox + x, oy + y, oz + z).color(r, g, b, a).uv(u, vv).endVertex();
    }

    // the bottom face has 4 position args less, so it uses this overload
    private void v(Matrix4f m, float ox, float oy, float oz, float x, float y, float u, float vv, float r, float g, float b, float a) {
        v(m, ox, oy, oz, x, y, 0, u, vv, r, g, b, a);
    }

    /**
     * Actually draws the stored boxes.
     */
    public void finish() {
        if (boxes != null && MMShaders.FANCYBOX != null) {
            boxes.setQuadSorting(VertexSorting.byDistance(0, 0, 0));

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);

            RenderSystem.setShader(() -> MMShaders.FANCYBOX);

            var time = MMShaders.FANCYBOX.getUniform("MMTime");
            if (time != null) time.set((System.currentTimeMillis() % 2500) / 1000f);

            Tesselator.getInstance().end();

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        } else if (boxes != null) {
            boxes.end();
        }

        boxes = null;
        pose = null;
    }

    /**
     * Draws long lines along every axis from the centre of the given block.
     */
    public void drawRulers(int x, int y, int z, float r, float g, float b, float a) {
        Matrix4f matrix = pose.last().pose();

        float cx = (float) (x + 0.5 - camera.x);
        float cy = (float) (y + 0.5 - camera.y);
        float cz = (float) (z + 0.5 - camera.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2f);

        BufferBuilder buffer = new BufferBuilder(256);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (Direction dir : Direction.values()) {
            buffer.vertex(matrix, cx, cy, cz).color(r, g, b, a).endVertex();
            buffer.vertex(matrix, cx + dir.getStepX() * RULER_LENGTH, cy + dir.getStepY() * RULER_LENGTH, cz + dir.getStepZ() * RULER_LENGTH)
                .color(r, g, b, a).endVertex();
        }

        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(buffer.end());

        RenderSystem.lineWidth(1f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
