package com.raishxn.modern_manipulator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.raishxn.modern_manipulator.common.item.MMCapability;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.Shape;
import com.raishxn.modern_manipulator.common.item.MMState.ToolMode;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;
import com.raishxn.modern_manipulator.common.network.MMNetwork;
import com.raishxn.modern_manipulator.common.network.ManipulatorConfigPacket;
import com.raishxn.modern_manipulator.common.network.ManipulatorConfigPacket.Action;

import java.util.ArrayList;
import java.util.List;

public class MMRadialMenuScreen extends Screen {

    private static final double TAU = Math.PI * 2.0D;
    private static final double INNER_RADIUS = 44.0D;
    private static final double OUTER_RADIUS = 124.0D;
    private static final int SEGMENTS = 16;

    private final ItemStack stack;
    private final List<RadialOption> options = new ArrayList<>();

    public MMRadialMenuScreen(ItemStack stack) {
        super(Component.translatable("screen.matter_manipulator.radial_menu"));
        this.stack = stack.copy();
    }

    @Override
    protected void init() {
        options.clear();
        if (!(stack.getItem() instanceof MatterManipulatorItem manipulator)) {
            return;
        }

        MMState state = MatterManipulatorItem.getState(stack);
        addModeOption(state, manipulator, ToolMode.GEOMETRY, Action.SET_MODE_GEOMETRY);
        addModeOption(state, manipulator, ToolMode.COPYING, Action.SET_MODE_COPYING);
        addModeOption(state, manipulator, ToolMode.EXCHANGING, Action.SET_MODE_EXCHANGING);
        addModeOption(state, manipulator, ToolMode.MOVING, Action.SET_MODE_MOVING);
        addModeOption(state, manipulator, ToolMode.CABLES, Action.SET_MODE_CABLES);
        addShapeOption(state, Shape.LINE, Action.SET_SHAPE_LINE);
        addShapeOption(state, Shape.CUBE, Action.SET_SHAPE_CUBE);
        addShapeOption(state, Shape.SPHERE, Action.SET_SHAPE_SPHERE);
        addShapeOption(state, Shape.CYLINDER, Action.SET_SHAPE_CYLINDER);
        layoutOptions();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int centerX = width / 2;
        int centerY = height / 2;
        double mouseRadius = distance(mouseX, mouseY, centerX, centerY);
        double mouseTheta = theta(mouseX, mouseY, centerX, centerY);

        for (RadialOption option : options) {
            boolean hovered = mouseRadius >= INNER_RADIUS && mouseRadius <= OUTER_RADIUS &&
                    isAngleBetween(mouseTheta, option.startTheta, option.endTheta);
            int color = option.selected ? 0xD0307EA6 : hovered ? 0xD03A3A3A : 0xC0101010;
            drawSlice(centerX, centerY, INNER_RADIUS, OUTER_RADIUS, option.startTheta, option.endTheta, color);
        }

        graphics.fill(centerX - 34, centerY - 34, centerX + 34, centerY + 34, 0xD0181818);
        graphics.renderItem(stack, centerX - 8, centerY - 24);
        graphics.drawCenteredString(font, centerText(), centerX, centerY, 0xFFE6E6E6);

        for (RadialOption option : options) {
            double angle = (option.startTheta + option.endTheta) / 2.0D;
            int labelX = (int) Math.round(centerX + Math.cos(angle) * ((INNER_RADIUS + OUTER_RADIUS) / 2.0D));
            int labelY = (int) Math.round(centerY + Math.sin(angle) * ((INNER_RADIUS + OUTER_RADIUS) / 2.0D));
            graphics.drawCenteredString(font, option.label, labelX, labelY - 4,
                    option.selected ? 0xFFFFFFFF : 0xFFCCCCCC);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;
        double mouseRadius = distance(mouseX, mouseY, centerX, centerY);
        double mouseTheta = theta(mouseX, mouseY, centerX, centerY);

        for (RadialOption option : options) {
            if (mouseRadius >= INNER_RADIUS && mouseRadius <= OUTER_RADIUS &&
                    isAngleBetween(mouseTheta, option.startTheta, option.endTheta)) {
                MMNetwork.CHANNEL.sendToServer(new ManipulatorConfigPacket(option.action));
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addModeOption(MMState state, MatterManipulatorItem manipulator, ToolMode mode, Action action) {
        if (canUseMode(state, manipulator.tier(), mode)) {
            options.add(new RadialOption(mode.displayName().getString(), action, state.mode() == mode));
        }
    }

    private void addShapeOption(MMState state, Shape shape, Action action) {
        options.add(new RadialOption(shape.displayName().getString(), action, state.shape() == shape));
    }

    private void layoutOptions() {
        if (options.isEmpty()) {
            return;
        }
        double slice = TAU / options.size();
        double start = -Math.PI / 2.0D - slice / 2.0D;
        for (int i = 0; i < options.size(); i++) {
            RadialOption option = options.get(i);
            option.startTheta = start + i * slice;
            option.endTheta = option.startTheta + slice;
        }
    }

    private Component centerText() {
        if (!(stack.getItem() instanceof MatterManipulatorItem)) {
            return Component.empty();
        }
        MMState state = MatterManipulatorItem.getState(stack);
        return Component
                .literal(state.mode().displayName().getString() + " / " + state.shape().displayName().getString());
    }

    private static boolean canUseMode(MMState state, MatterManipulatorItem.ManipulatorTier tier, ToolMode mode) {
        return switch (mode) {
            case GEOMETRY -> state.hasCapability(tier, MMCapability.ALLOW_GEOMETRY);
            case COPYING -> state.hasCapability(tier, MMCapability.ALLOW_COPYING);
            case EXCHANGING -> state.hasCapability(tier, MMCapability.ALLOW_EXCHANGING);
            case MOVING -> state.hasCapability(tier, MMCapability.ALLOW_MOVING);
            case CABLES -> state.hasCapability(tier, MMCapability.ALLOW_CABLES);
        };
    }

    private static void drawSlice(int centerX, int centerY, double innerRadius, double outerRadius,
                                  double startTheta, double endTheta, int color) {
        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= SEGMENTS; i++) {
            double theta = startTheta + (endTheta - startTheta) * i / SEGMENTS;
            buffer.vertex(centerX + Math.cos(theta) * outerRadius, centerY + Math.sin(theta) * outerRadius, 0)
                    .color(red, green, blue, alpha)
                    .endVertex();
            buffer.vertex(centerX + Math.cos(theta) * innerRadius, centerY + Math.sin(theta) * innerRadius, 0)
                    .color(red, green, blue, alpha)
                    .endVertex();
        }
        Tesselator.getInstance().end();
        RenderSystem.disableBlend();
    }

    private static double distance(double mouseX, double mouseY, int centerX, int centerY) {
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double theta(double mouseX, double mouseY, int centerX, int centerY) {
        return modTau(Math.atan2(mouseY - centerY, mouseX - centerX));
    }

    private static double modTau(double angle) {
        return ((angle % TAU) + TAU) % TAU;
    }

    private static boolean isAngleBetween(double target, double start, double end) {
        return modTau(target - start) < end - start;
    }

    private static class RadialOption {

        private final String label;
        private final Action action;
        private final boolean selected;
        private double startTheta;
        private double endTheta;

        private RadialOption(String label, Action action, boolean selected) {
            this.label = label;
            this.action = action;
            this.selected = selected;
        }
    }
}
