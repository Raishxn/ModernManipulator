package com.raishxn.modern_manipulator.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A radial menu that fills the whole screen.
 */
public class RadialMenu {

    private static final double TAU = Math.PI * 2;

    public List<RadialMenuOption> options = new ArrayList<>();
    public float innerRadius = 0.25f, outerRadius = 0.60f;
    /**
     * An icon to draw in the centre of the menu, or null to skip it.
     */
    public ItemStack innerIcon;

    /** The screen that hosts this menu */
    public RadialMenuScreen screen;

    public void draw(GuiGraphics graphics, int width, int height, double mouseX, double mouseY) {
        double weightSum = 0;

        // calculate the total weight sum
        for (RadialMenuOption option : options) {
            option.isHidden = option.hidden.getAsBoolean();

            if (!option.isHidden) {
                weightSum += option.weight;
            }
        }

        double currentAngle = 0;

        // lay out the options
        for (RadialMenuOption option : options) {
            if (option.isHidden) {
                option.startTheta = 0;
                option.endTheta = 0;
                continue;
            }

            double sliceSize = option.weight / weightSum * TAU;

            option.startTheta = currentAngle;
            currentAngle += sliceSize;
            option.endTheta = currentAngle;
        }

        RadialMenuOption firstShown = null;

        for (RadialMenuOption option : options) {
            if (!option.isHidden) {
                firstShown = option;
                break;
            }
        }

        // shift the options by half the width of the first option, to make it look better
        if (firstShown != null) {
            double offset = Math.abs(firstShown.startTheta - firstShown.endTheta) / 2;

            for (RadialMenuOption option : options) {
                if (!option.isHidden) {
                    option.startTheta -= offset;
                    option.endTheta -= offset;
                }
            }
        }

        PoseStack pose = graphics.pose();

        pose.pushPose();

        pose.translate(width / 2f, height / 2f, 0);

        int dim = Math.min(width, height);

        double[] mouse = getMousePosition(width, height, mouseX, mouseY);
        double mouseRadius = mouse[0];
        double mouseTheta = mouse[1];

        pose.pushPose();
        // convert from screen space into a centered square w/ bounds [-1, 1] space
        pose.scale(dim / 2f, dim / 2f, 1);

        Matrix4f matrix = pose.last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();

        BufferBuilder buffer = Tesselator.getInstance().getBuilder();

        // draw the options
        for (RadialMenuOption option : options) {
            if (option.isHidden) continue;

            boolean isHoveredOver = mouseRadius >= innerRadius && mouseRadius <= outerRadius &&
                    isAngleBetween(mouseTheta, option.startTheta, option.endTheta);

            float c = isHoveredOver ? 0.25f : 0f;

            buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

            double step = Math.PI / 32;

            for (int i = 0; true; i++) {
                double t = option.startTheta + i * step;
                double clamped = Math.min(Math.max(t, option.startTheta), option.endTheta);

                radialVertex(buffer, matrix, outerRadius, clamped, c);
                radialVertex(buffer, matrix, innerRadius, clamped, c);

                if (t > option.endTheta) break;
            }

            Tesselator.getInstance().end();
        }

        RenderSystem.enableCull();

        pose.popPose();

        if (innerIcon != null && !innerIcon.isEmpty()) {
            pose.pushPose();
            pose.scale(2, 2, 1);
            graphics.renderItem(innerIcon, -8, -8);
            pose.popPose();
        }

        pose.popPose();

        // draw the options' labels
        for (RadialMenuOption option : options) {
            if (option.isHidden) continue;

            radialText(
                    graphics,
                    width,
                    height,
                    (innerRadius + outerRadius) / 2,
                    (option.startTheta + option.endTheta) / 2,
                    60, // hardcoded wordwrap width, not great but idk how to fix it
                    0xFFCCCCCC,
                    option.label.get());
        }
    }

    public boolean onClick(int width, int height, double mouseX, double mouseY, int mouseButton) {
        double[] mouse = getMousePosition(width, height, mouseX, mouseY);
        double mouseRadius = mouse[0];
        double mouseTheta = mouse[1];

        for (RadialMenuOption option : new ArrayList<>(options)) {
            boolean isHoveredOver = mouseRadius >= innerRadius && mouseRadius <= outerRadius &&
                    isAngleBetween(mouseTheta, option.startTheta, option.endTheta);

            if (isHoveredOver) {
                if (option.hidden.getAsBoolean()) return true;

                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1f, 0.5f));

                if (option.onClick != null) option.onClick.onClick(this, option, mouseButton, false);

                return true;
            }
        }

        return false;
    }

    private static double mod_tau(double angle) {
        return ((angle % TAU) + TAU) % TAU;
    }

    private static boolean isAngleBetween(double target, double angle1, double angle2) {
        if (angle2 < angle1) return false;

        while (angle1 < 0) {
            angle1 += TAU;
            angle2 += TAU;
        }

        while (angle1 > TAU) {
            angle1 -= TAU;
            angle2 -= TAU;
        }

        return mod_tau(target - angle1) < angle2 - angle1;
    }

    /**
     * Gets the mouse position in terms of theta and radius, instead of x,y.
     */
    private static double[] getMousePosition(int width, int height, double mouseX, double mouseY) {
        int dim = Math.min(width, height);

        double mx = map(mouseX, width / 2f - dim / 2f, width / 2f + dim / 2f, -1, 1);
        double my = map(mouseY, height / 2f - dim / 2f, height / 2f + dim / 2f, -1, 1);

        double mouseRadius = Math.sqrt(mx * mx + my * my);
        double mouseTheta = mod_tau(Math.atan2(my, mx));

        return new double[] { mouseRadius, mouseTheta };
    }

    private static void radialText(GuiGraphics graphics, int width, int height, double radius, double theta,
                                   int wrapWidth,
                                   int color, String text) {
        Font font = Minecraft.getInstance().font;

        int dim = Math.min(width, height);

        int x = (int) map(Math.cos(theta) * radius, -1, 1, width / 2f - dim / 2f, width / 2f + dim / 2f);
        int y = (int) map(Math.sin(theta) * radius, -1, 1, height / 2f - dim / 2f, height / 2f + dim / 2f);

        List<FormattedCharSequence> lines = font.split(Component.literal(text), wrapWidth);

        int boundsX = 0;
        int boundsY = lines.size() * font.lineHeight;

        for (FormattedCharSequence line : lines) {
            boundsX = Math.max(boundsX, font.width(line));
        }

        int nextY = y - boundsY / 2;

        for (FormattedCharSequence line : lines) {
            int lineWidth = font.width(line);

            int paddingX = (boundsX - lineWidth) / 2;

            graphics.drawString(font, line, x - boundsX / 2 + paddingX, nextY, color, false);

            nextY += font.lineHeight;
        }
    }

    private static double map(double x, double in_min, double in_max, double out_min, double out_max) {
        return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
    }

    private static void radialVertex(BufferBuilder buffer, Matrix4f matrix, double radius, double theta, float c) {
        float x = (float) (Math.cos(theta) * radius);
        float y = (float) (Math.sin(theta) * radius);

        buffer.vertex(matrix, x, y, 0).color(c, c, c, 1f).endVertex();
    }

    public static class RadialMenuOption {

        public Supplier<String> label;
        public double weight = 1;

        public BooleanSupplier hidden = () -> false;

        boolean isHidden;

        public RadialMenuClickHandler onClick;

        public double startTheta, endTheta;
    }

    public interface RadialMenuClickHandler {

        void onClick(RadialMenu menu, RadialMenuOption option, int mouseButton, boolean doubleClicked);
    }
}
