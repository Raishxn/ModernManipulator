package com.raishxn.modern_manipulator.client.gui;

import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the world axes as seen from the camera (like the F3 crosshair), so that the transform directions can be
 * related to the world.
 * "borrowed" from angelica (via the original manipulator).
 */
public class DirectionDrawable {

    private DirectionDrawable() {}

    public static void draw(GuiGraphics graphics, Transform transform, int cx, int cy, int length) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        Quaternionf rot = new Quaternionf().rotateX((float) Math.toRadians(camera.getXRot()))
            .rotateY((float) Math.toRadians(camera.getYRot() + 180));

        drawAxis(graphics, rot, new Vector3f(1, 0, 0), cx, cy, length, 0xFFFF0000);
        drawAxis(graphics, rot, new Vector3f(0, 0, 1), cx, cy, length, 0xFF4B4BFF);
        drawAxis(graphics, rot, new Vector3f(0, 1, 0), cx, cy, length, 0xFF00FF00);
    }

    private static void drawAxis(GuiGraphics graphics, Quaternionf rot, Vector3f axis, int cx, int cy, int length, int color) {
        Vector3f v = rot.transform(new Vector3f(axis));

        int ex = cx + Math.round(-v.x * length);
        int ey = cy + Math.round(-v.y * length);

        line(graphics, cx, cy, ex, ey, color);
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);

            if (x0 == x1 && y0 == y1) break;

            int e2 = 2 * err;

            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }

            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }
}
