package com.raishxn.modern_manipulator.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.raishxn.modern_manipulator.common.item.MMCapability;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.BlockSelectMode;
import com.raishxn.modern_manipulator.common.item.MMState.Shape;
import com.raishxn.modern_manipulator.common.item.MMState.ToolMode;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;
import com.raishxn.modern_manipulator.common.network.MMNetwork;
import com.raishxn.modern_manipulator.common.network.ManipulatorConfigPacket;
import com.raishxn.modern_manipulator.common.network.ManipulatorConfigPacket.Action;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MMRadialMenuScreen extends Screen {

    private static final double TAU = Math.PI * 2.0D;
    private static final double INNER_RADIUS_SCALE = 0.125D;
    private static final double OUTER_RADIUS_SCALE = 0.300D;
    private static final int SEGMENTS = 32;
    private static final int LABEL_WIDTH = 60;

    private final ItemStack stack;
    private final ArrayDeque<RadialPage> pageStack = new ArrayDeque<>();
    private final List<RadialOption> options = new ArrayList<>();

    public MMRadialMenuScreen(ItemStack stack) {
        super(Component.translatable("screen.matter_manipulator.radial_menu"));
        this.stack = stack.copy();
    }

    @Override
    protected void init() {
        pageStack.clear();
        pushPage(buildRootPage());
    }

    private RadialPage buildRootPage() {
        options.clear();
        if (!(stack.getItem() instanceof MatterManipulatorItem manipulator)) {
            return new RadialPage(Component.translatable("screen.matter_manipulator.radial_menu"));
        }

        MMState state = MatterManipulatorItem.getState(stack);
        return switch (state.mode()) {
            case COPYING -> state.hasCapability(manipulator.tier(), MMCapability.ALLOW_COPYING) ?
                    buildCopyingContextPage(state, manipulator) : buildGeometryContextPage(state, manipulator);
            case MOVING -> state.hasCapability(manipulator.tier(), MMCapability.ALLOW_MOVING) ?
                    buildMovingContextPage(state, manipulator) : buildGeometryContextPage(state, manipulator);
            case EXCHANGING -> state.hasCapability(manipulator.tier(), MMCapability.ALLOW_EXCHANGING) ?
                    buildExchangeContextPage(state, manipulator) : buildGeometryContextPage(state, manipulator);
            case CABLES -> state.hasCapability(manipulator.tier(), MMCapability.ALLOW_CABLES) ?
                    buildCableContextPage(state, manipulator) : buildGeometryContextPage(state, manipulator);
            case GEOMETRY -> buildGeometryContextPage(state, manipulator);
        };
    }

    private RadialPage buildModePage(MMState state, MatterManipulatorItem manipulator) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.set_mode"));
        addModeOption(page, state, manipulator, ToolMode.EXCHANGING, Action.SET_MODE_EXCHANGING);
        addModeOption(page, state, manipulator, ToolMode.CABLES, Action.SET_MODE_CABLES);
        if (state.hasCapability(manipulator.tier(), MMCapability.ALLOW_REMOVING)) {
            page.branch("matter_manipulator.radial.set_remove_mode", buildRemoveModePage(state));
        }
        addModeOption(page, state, manipulator, ToolMode.GEOMETRY, Action.SET_MODE_GEOMETRY);
        addModeOption(page, state, manipulator, ToolMode.MOVING, Action.SET_MODE_MOVING);
        addModeOption(page, state, manipulator, ToolMode.COPYING, Action.SET_MODE_COPYING);
        return page;
    }

    private RadialPage buildShapePage(MMState state) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.set_shape"));
        addShapeOption(page, state, Shape.CYLINDER, Action.SET_SHAPE_CYLINDER);
        addShapeOption(page, state, Shape.SPHERE, Action.SET_SHAPE_SPHERE);
        addShapeOption(page, state, Shape.LINE, Action.SET_SHAPE_LINE);
        addShapeOption(page, state, Shape.CUBE, Action.SET_SHAPE_CUBE);
        return page;
    }

    private RadialPage buildMoveCoordsPage() {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.move_coords"));
        page.action("matter_manipulator.radial.move_here", Action.MOVE_SELECTION_HERE);
        page.action("matter_manipulator.radial.move_coord_b", Action.MOVE_COORD_B);
        page.action("matter_manipulator.radial.move_coord_a", Action.MOVE_COORD_A);
        page.action("matter_manipulator.radial.move_all", Action.MOVE_ALL_COORDS);
        return page;
    }

    private RadialPage buildTransformPage() {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.transform"));
        page.action("matter_manipulator.radial.rotate_cw", Action.ROTATE_CW);
        page.action("matter_manipulator.radial.rotate_ccw", Action.ROTATE_CCW);
        page.action("matter_manipulator.radial.mirror_x", Action.MIRROR_X);
        page.action("matter_manipulator.radial.mirror_y", Action.MIRROR_Y);
        page.action("matter_manipulator.radial.mirror_z", Action.MIRROR_Z);
        page.branch("matter_manipulator.radial.array", buildArrayPage());
        page.branch("matter_manipulator.radial.offset", buildOffsetPage());
        page.action("matter_manipulator.radial.reset_transform", Action.RESET_TRANSFORM);
        return page;
    }

    private RadialPage buildArrayPage() {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.array"));
        page.action("matter_manipulator.radial.array_x_plus", Action.ARRAY_X_PLUS);
        page.action("matter_manipulator.radial.array_x_minus", Action.ARRAY_X_MINUS);
        page.action("matter_manipulator.radial.array_y_plus", Action.ARRAY_Y_PLUS);
        page.action("matter_manipulator.radial.array_y_minus", Action.ARRAY_Y_MINUS);
        page.action("matter_manipulator.radial.array_z_plus", Action.ARRAY_Z_PLUS);
        page.action("matter_manipulator.radial.array_z_minus", Action.ARRAY_Z_MINUS);
        page.action("matter_manipulator.radial.array_reset", Action.ARRAY_RESET);
        return page;
    }

    private RadialPage buildOffsetPage() {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.offset"));
        page.action("matter_manipulator.radial.offset_x_plus", Action.OFFSET_X_PLUS);
        page.action("matter_manipulator.radial.offset_x_minus", Action.OFFSET_X_MINUS);
        page.action("matter_manipulator.radial.offset_y_plus", Action.OFFSET_Y_PLUS);
        page.action("matter_manipulator.radial.offset_y_minus", Action.OFFSET_Y_MINUS);
        page.action("matter_manipulator.radial.offset_z_plus", Action.OFFSET_Z_PLUS);
        page.action("matter_manipulator.radial.offset_z_minus", Action.OFFSET_Z_MINUS);
        page.action("matter_manipulator.radial.offset_reset", Action.OFFSET_RESET);
        return page;
    }

    private RadialPage buildRemoveModePage(MMState state) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.set_remove_mode"));
        page.action("matter_manipulator.radial.remove_all", Action.SET_REMOVE_ALL,
                state.removeMode() == MMState.RemoveMode.ALL);
        page.action("matter_manipulator.radial.remove_none", Action.SET_REMOVE_NONE,
                state.removeMode() == MMState.RemoveMode.NONE);
        page.action("matter_manipulator.radial.remove_replaceable", Action.SET_REMOVE_REPLACEABLE,
                state.removeMode() == MMState.RemoveMode.REPLACEABLE);
        return page;
    }

    private RadialPage buildGeometryContextPage(MMState state, MatterManipulatorItem manipulator) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.geometry_options"));
        page.branch("matter_manipulator.radial.move_coords", buildMoveCoordsPage());
        if (state.hasCapability(manipulator.tier(), MMCapability.ALLOW_EXCHANGING)) {
            page.branch("matter_manipulator.radial.set_shape", buildShapePage(state));
            page.branch("matter_manipulator.radial.set_mode", buildModePage(state, manipulator));
            page.branch("matter_manipulator.radial.select_blocks", buildBlockSelectPage(state));
        } else {
            page.branch("matter_manipulator.radial.select_blocks", buildBlockSelectPage(state));
            page.branch("matter_manipulator.radial.set_shape", buildShapePage(state));
        }
        return page;
    }

    private RadialPage buildCopyingContextPage(MMState state, MatterManipulatorItem manipulator) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.copying_options"));
        page.branch("matter_manipulator.radial.transform", buildTransformPage());
        page.action("matter_manipulator.radial.mark_paste", Action.PREPARE_PASTE);
        page.branch("matter_manipulator.radial.set_mode", buildModePage(state, manipulator));
        page.action("matter_manipulator.radial.mark_copy", Action.PREPARE_COPY);
        page.branch("matter_manipulator.radial.edit_stack", buildArrayPage());
        page.branch("matter_manipulator.radial.planning", buildPlanningPage());
        return page;
    }

    private RadialPage buildMovingContextPage(MMState state, MatterManipulatorItem manipulator) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.moving_options"));
        page.action("matter_manipulator.radial.mark_paste", Action.PREPARE_PASTE);
        page.branch("matter_manipulator.radial.set_mode", buildModePage(state, manipulator));
        page.action("matter_manipulator.radial.mark_cut", Action.PREPARE_MOVE);
        return page;
    }

    private RadialPage buildExchangeContextPage(MMState state, MatterManipulatorItem manipulator) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.exchanging_options"));
        page.branch("matter_manipulator.radial.move_coords", buildMoveCoordsPage());
        page.branch("matter_manipulator.radial.set_mode", buildModePage(state, manipulator));
        page.action("matter_manipulator.radial.set_block_to_replace_with", Action.SET_EXCHANGE_REPLACEMENT,
                !state.exchangeReplacement().isEmpty());
        page.branch("matter_manipulator.radial.edit_replace_whitelist", buildExchangeWhitelistPage(state));
        return page;
    }

    private RadialPage buildExchangeWhitelistPage(MMState state) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.edit_replace_whitelist"));
        page.action("matter_manipulator.radial.add_replace_whitelist_block", Action.ADD_EXCHANGE_WHITELIST_BLOCK);
        page.action("matter_manipulator.radial.clear_replace_whitelist", Action.CLEAR_EXCHANGE_WHITELIST,
                false);
        return page;
    }

    private RadialPage buildCableContextPage(MMState state, MatterManipulatorItem manipulator) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.cable_options"));
        page.branch("matter_manipulator.radial.move_coords", buildMoveCoordsPage());
        page.branch("matter_manipulator.radial.set_mode", buildModePage(state, manipulator));
        page.action("matter_manipulator.radial.set_cable", Action.SET_CABLE, !state.cableStack().isEmpty());
        page.action("matter_manipulator.radial.clear_cable", Action.CLEAR_CABLE);
        return page;
    }

    private RadialPage buildPlanningPage() {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.planning"));
        page.action("matter_manipulator.radial.plan_missing_manual", Action.PLAN_MISSING_MANUAL);
        page.action("matter_manipulator.radial.plan_missing_auto", Action.PLAN_MISSING_AUTO);
        page.action("matter_manipulator.radial.plan_all_manual", Action.PLAN_ALL_MANUAL);
        page.action("matter_manipulator.radial.plan_all_auto", Action.PLAN_ALL_AUTO);
        return page;
    }

    private RadialPage buildBlockSelectPage(MMState state) {
        RadialPage page = new RadialPage(Component.translatable("matter_manipulator.radial.select_blocks"));
        page.action("matter_manipulator.radial.select_all", Action.SET_BLOCK_SELECT_ALL,
                state.blockSelectMode() == BlockSelectMode.ALL);
        page.action("matter_manipulator.radial.clear_all", Action.SET_BLOCK_SELECT_NONE,
                state.blockSelectMode() == BlockSelectMode.NONE);
        page.action("matter_manipulator.radial.select_volumes", Action.SET_BLOCK_SELECT_VOLUMES,
                state.blockSelectMode() == BlockSelectMode.VOLUMES);
        page.action("matter_manipulator.radial.select_corners", Action.SET_BLOCK_SELECT_CORNERS,
                state.blockSelectMode() == BlockSelectMode.CORNERS);
        page.action("matter_manipulator.radial.select_edges", Action.SET_BLOCK_SELECT_EDGES,
                state.blockSelectMode() == BlockSelectMode.EDGES);
        page.action("matter_manipulator.radial.select_faces", Action.SET_BLOCK_SELECT_FACES,
                state.blockSelectMode() == BlockSelectMode.FACES);
        return page;
    }

    private void pushPage(RadialPage page) {
        pageStack.push(page);
        showPage(page);
    }

    private void popPage() {
        if (pageStack.size() <= 1) {
            return;
        }
        pageStack.pop();
        showPage(pageStack.peek());
    }

    private void showPage(RadialPage page) {
        options.clear();
        if (pageStack.size() > 1) {
            options.add(RadialOption.back());
        }
        options.addAll(page.options);
        layoutOptions();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = width / 2;
        int centerY = height / 2;
        double innerRadius = innerRadius();
        double outerRadius = outerRadius();
        double mouseRadius = distance(mouseX, mouseY, centerX, centerY);
        double mouseTheta = theta(mouseX, mouseY, centerX, centerY);

        for (RadialOption option : options) {
            boolean hovered = mouseRadius >= innerRadius && mouseRadius <= outerRadius &&
                    isAngleBetween(mouseTheta, option.startTheta, option.endTheta);
            int color = option.selected ? 0xFF15455C : hovered ? 0xFF404040 : 0xFF000000;
            drawSlice(centerX, centerY, innerRadius, outerRadius, option.startTheta, option.endTheta, color);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(centerX - 16, centerY - 16, 0);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();

        for (RadialOption option : options) {
            double angle = (option.startTheta + option.endTheta) / 2.0D;
            int labelX = (int) Math.round(centerX + Math.cos(angle) * ((innerRadius + outerRadius) / 2.0D));
            int labelY = (int) Math.round(centerY + Math.sin(angle) * ((innerRadius + outerRadius) / 2.0D));
            drawWrappedCenteredString(graphics, option.label, labelX, labelY,
                    option.selected ? 0xFFFFFFFF : 0xFFCCCCCC);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        int centerY = height / 2;
        double innerRadius = innerRadius();
        double outerRadius = outerRadius();
        double mouseRadius = distance(mouseX, mouseY, centerX, centerY);
        double mouseTheta = theta(mouseX, mouseY, centerX, centerY);

        for (RadialOption option : options) {
            if (mouseRadius >= innerRadius && mouseRadius <= outerRadius &&
                    isAngleBetween(mouseTheta, option.startTheta, option.endTheta)) {
                if (option.back) {
                    popPage();
                } else if (option.childPage != null) {
                    pushPage(option.childPage);
                } else if (option.action != null) {
                    MMNetwork.CHANNEL.sendToServer(new ManipulatorConfigPacket(option.action));
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void addModeOption(RadialPage page, MMState state, MatterManipulatorItem manipulator, ToolMode mode,
                               Action action) {
        if (canUseMode(state, manipulator.tier(), mode)) {
            page.action(mode.displayName(), action, state.mode() == mode);
        }
    }

    private void addShapeOption(RadialPage page, MMState state, Shape shape, Action action) {
        page.action(shape.displayName(), action, state.shape() == shape);
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

    private static boolean canUseMode(MMState state, MatterManipulatorItem.ManipulatorTier tier, ToolMode mode) {
        return switch (mode) {
            case GEOMETRY -> state.hasCapability(tier, MMCapability.ALLOW_GEOMETRY);
            case COPYING -> state.hasCapability(tier, MMCapability.ALLOW_COPYING);
            case EXCHANGING -> state.hasCapability(tier, MMCapability.ALLOW_EXCHANGING);
            case MOVING -> state.hasCapability(tier, MMCapability.ALLOW_MOVING);
            case CABLES -> state.hasCapability(tier, MMCapability.ALLOW_CABLES);
        };
    }

    private double innerRadius() {
        return Math.min(width, height) * INNER_RADIUS_SCALE;
    }

    private double outerRadius() {
        return Math.min(width, height) * OUTER_RADIUS_SCALE;
    }

    private void drawWrappedCenteredString(GuiGraphics graphics, String label, int centerX, int centerY, int color) {
        List<FormattedCharSequence> lines = font.split(Component.literal(label), LABEL_WIDTH);
        int totalHeight = lines.size() * font.lineHeight;
        int y = centerY - totalHeight / 2;
        for (FormattedCharSequence line : lines) {
            int x = centerX - font.width(line) / 2;
            graphics.drawString(font, line, x, y, color, false);
            y += font.lineHeight;
        }
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
        private final RadialPage childPage;
        private final boolean selected;
        private final boolean back;
        private double startTheta;
        private double endTheta;

        private RadialOption(String label, Action action, boolean selected) {
            this(label, action, null, selected, false);
        }

        private RadialOption(String label, RadialPage childPage) {
            this(label, null, childPage, false, false);
        }

        private RadialOption(String label, Action action, RadialPage childPage, boolean selected, boolean back) {
            this.label = label;
            this.action = action;
            this.childPage = childPage;
            this.selected = selected;
            this.back = back;
        }

        private static RadialOption back() {
            return new RadialOption(Component.translatable("matter_manipulator.radial.back").getString(), null, null,
                    false, true);
        }
    }

    private static class RadialPage {

        private final Component title;
        private final List<RadialOption> options = new ArrayList<>();

        private RadialPage(Component title) {
            this.title = title;
        }

        private void action(String translationKey, Action action) {
            action(Component.translatable(translationKey), action, false);
        }

        private void action(String translationKey, Action action, boolean selected) {
            action(Component.translatable(translationKey), action, selected);
        }

        private void action(Component label, Action action, boolean selected) {
            options.add(new RadialOption(label.getString(), action, selected));
        }

        private void branch(String translationKey, RadialPage childPage) {
            options.add(new RadialOption(Component.translatable(translationKey).getString(), childPage));
        }
    }
}
