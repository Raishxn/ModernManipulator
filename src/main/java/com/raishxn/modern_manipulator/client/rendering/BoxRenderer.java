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
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws region boxes and rulers.
 */
public class BoxRenderer {

    public static final BoxRenderer INSTANCE = new BoxRenderer();

    private static final int RULER_LENGTH = 128;

    private PoseStack pose;
    private Vec3 camera;

    public void start(PoseStack pose, Vec3 camera) {
        this.pose = pose;
        this.camera = camera;
    }

    public void finish() {
        this.pose = null;
    }

    /**
     * Draws a translucent box with an outline around the given bounding box.
     */
    public void drawAround(AABB aabb, Vector3f color) {
        AABB box = aabb.inflate(0.002).move(-camera.x, -camera.y, -camera.z);

        Matrix4f matrix = pose.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // faces
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float r = color.x, g = color.y, b = color.z, a = 0.15f;

        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;

        quad(buffer, matrix, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        quad(buffer, matrix, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(buffer, matrix, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        quad(buffer, matrix, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        quad(buffer, matrix, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
        quad(buffer, matrix, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);

        Tesselator.getInstance().end();

        // outline
        RenderSystem.lineWidth(2f);
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        float la = 0.9f;

        line(buffer, matrix, x0, y0, z0, x1, y0, z0, r, g, b, la);
        line(buffer, matrix, x0, y0, z1, x1, y0, z1, r, g, b, la);
        line(buffer, matrix, x0, y1, z0, x1, y1, z0, r, g, b, la);
        line(buffer, matrix, x0, y1, z1, x1, y1, z1, r, g, b, la);

        line(buffer, matrix, x0, y0, z0, x0, y1, z0, r, g, b, la);
        line(buffer, matrix, x1, y0, z0, x1, y1, z0, r, g, b, la);
        line(buffer, matrix, x0, y0, z1, x0, y1, z1, r, g, b, la);
        line(buffer, matrix, x1, y0, z1, x1, y1, z1, r, g, b, la);

        line(buffer, matrix, x0, y0, z0, x0, y0, z1, r, g, b, la);
        line(buffer, matrix, x1, y0, z0, x1, y0, z1, r, g, b, la);
        line(buffer, matrix, x0, y1, z0, x0, y1, z1, r, g, b, la);
        line(buffer, matrix, x1, y1, z0, x1, y1, z1, r, g, b, la);

        Tesselator.getInstance().end();

        RenderSystem.lineWidth(1f);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
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

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (Direction dir : Direction.values()) {
            line(
                buffer,
                matrix,
                cx,
                cy,
                cz,
                cx + dir.getStepX() * RULER_LENGTH,
                cy + dir.getStepY() * RULER_LENGTH,
                cz + dir.getStepZ() * RULER_LENGTH,
                r,
                g,
                b,
                a);
        }

        Tesselator.getInstance().end();

        RenderSystem.lineWidth(1f);
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder buffer, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float cx,
                             float cy, float cz, float dx, float dy, float dz, float r, float g, float b, float a) {
        buffer.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        buffer.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
        buffer.vertex(m, cx, cy, cz).color(r, g, b, a).endVertex();
        buffer.vertex(m, dx, dy, dz).color(r, g, b, a).endVertex();
    }

    private static void line(BufferBuilder buffer, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float r,
                             float g, float b, float a) {
        buffer.vertex(m, ax, ay, az).color(r, g, b, a).endVertex();
        buffer.vertex(m, bx, by, bz).color(r, g, b, a).endVertex();
    }
}
