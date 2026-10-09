package com.raishxn.modern_manipulator.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMConfig.VoxelAABB;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.Shape;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;
import com.raishxn.modern_manipulator.common.networking.Messages;
import com.raishxn.modern_manipulator.common.utils.MMUtils;
import org.joml.Vector3i;
import org.joml.Vector3ic;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.UP;

/**
 * The transform/coordinate editor (port of the original TransformWindow).
 */
public class TransformScreen extends Screen {

    private final PlaceMode mode;

    /** A row in the right column. Either a header or a coordinate editor. */
    private interface Row {

        int height();

        void layout(int x, int y);

        default void render(GuiGraphics graphics) {}

        default void tick() {}
    }

    private final List<List<Row>> columns = new ArrayList<>();
    private final List<Row> leftRows = new ArrayList<>();

    public TransformScreen(PlaceMode mode) {
        super(Component.translatable("mm.gui.edit_transform"));
        this.mode = mode;
    }

    private Player player() {
        return Minecraft.getInstance().player;
    }

    private ItemStack getStack() {
        return player().getItemInHand(InteractionHand.MAIN_HAND);
    }

    private MMState getState() {
        ItemStack stack = getStack();

        if (!(stack.getItem() instanceof ItemMatterManipulator)) return null;

        return ItemMatterManipulator.getState(stack);
    }

    @Override
    protected void init() {
        columns.clear();
        leftRows.clear();

        if (getState() == null) {
            onClose();
            return;
        }

        switch (mode) {
            case CABLES -> buildLineShape();
            case EXCHANGING -> buildCubeShape();
            case MOVING -> buildMoveMode();
            case COPYING -> buildCopyMode();
            case GEOMETRY -> buildGeomMode();
        }

        layoutColumns();
    }

    // #region Layout

    private static final int ROW_WIDTH = 130;

    private void layoutColumns() {
        int x = width - 10 - ROW_WIDTH;

        for (List<Row> column : columns) {
            int total = 0;
            for (Row row : column) total += row.height();

            int y = Math.max(4, (height - total) / 2);

            for (Row row : column) {
                row.layout(x, y);
                y += row.height();
            }

            x -= ROW_WIDTH + 10;
        }

        int total = 0;
        for (Row row : leftRows) total += row.height();

        int y = Math.max(4, (height - total) / 2);

        for (Row row : leftRows) {
            row.layout(10, y);
            y += row.height();
        }
    }

    /**
     * Adds the rows, splitting them into two columns when there isn't enough vertical space (same as the original's
     * gui scale check).
     */
    private void addColumns(List<Row> primary, List<Row> secondary) {
        int total = 0;
        for (Row row : primary) total += row.height();
        for (Row row : secondary) total += row.height();

        if (total <= height - 8) {
            List<Row> merged = new ArrayList<>(primary);
            merged.addAll(secondary);
            columns.add(merged);
        } else {
            columns.add(primary);
            if (!secondary.isEmpty()) columns.add(secondary);
        }
    }

    private Row padding(int h) {
        return new Row() {

            @Override
            public int height() {
                return h;
            }

            @Override
            public void layout(int x, int y) {}
        };
    }

    private Row header(String text) {
        return new Row() {

            int x, y;

            @Override
            public int height() {
                return 20;
            }

            @Override
            public void layout(int x, int y) {
                this.x = x;
                this.y = y;
            }

            @Override
            public void render(GuiGraphics graphics) {
                int w = 60;
                int left = x + (ROW_WIDTH - w) / 2;

                graphics.fill(left, y, left + w, y + 18, 0xFF888888);
                graphics.fill(left + 2, y + 2, left + w - 2, y + 16, 0xFF111111);
                graphics.drawCenteredString(font, text, left + w / 2, y + 5, 0xFFDDDDDD);
            }
        };
    }

    private Row widgetRow(int h, AbstractWidget... widgets) {
        for (AbstractWidget widget : widgets) addRenderableWidget(widget);

        return new Row() {

            @Override
            public int height() {
                return h;
            }

            @Override
            public void layout(int x, int y) {
                int cursor = x;

                for (AbstractWidget widget : widgets) {
                    widget.setX(cursor);
                    widget.setY(y);
                    cursor += widget.getWidth() + 5;
                }
            }
        };
    }

    // #endregion

    // #region Coordinate editors

    private enum Coord {
        Copy,
        CopyA,
        CopyB,
        Paste,
        Stack
    }

    private enum CoordComponent {

        X,
        Y,
        Z;

        public int get(Vector3ic v) {
            return switch (this) {
                case X -> v.x();
                case Y -> v.y();
                case Z -> v.z();
            };
        }

        public void set(Vector3i v, int k) {
            switch (this) {
                case X -> v.x = k;
                case Y -> v.y = k;
                case Z -> v.z = k;
            }
        }
    }

    private Row makeCoordinateEditor(Coord coord, CoordComponent component) {
        IntSupplier getter = createGetter(coord, component);
        IntConsumer setter = createSetter(coord, component);

        IntSupplier getterVisual = () -> {
            int k = getter.getAsInt();

            if (coord == Coord.Stack) {
                if (k >= 0) k++;
            }

            return k;
        };

        Button minus = Button.builder(Component.literal(component.name() + " - 1"), b -> {
            int i = getter.getAsInt();
            i -= getOffset(coord, component);
            setter.accept(i);
        }).size(40, 18).build();

        Button plus = Button.builder(Component.literal(component.name() + " + 1"), b -> {
            int i = getter.getAsInt();
            i += getOffset(coord, component);
            setter.accept(i);
        }).size(40, 18).build();

        AbstractWidget middle;

        if (coord != Coord.Copy) {
            EditBox box = new EditBox(font, 0, 0, 36, 16, Component.literal(component.name()));
            box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
            box.setValue(Integer.toString(getterVisual.getAsInt()));
            box.setResponder(s -> {
                if (!box.isFocused()) return;

                try {
                    int value = Integer.parseInt(s);

                    if (coord == Coord.Stack && value > 0) value--;

                    if (value != getter.getAsInt()) setter.accept(value);
                } catch (NumberFormatException ignored) {}
            });
            middle = box;
        } else {
            middle = Button.builder(Component.literal("N/A"), b -> {}).size(40, 18).build();
            middle.active = false;
        }

        Row row = widgetRow(20, minus, middle, plus);

        return new Row() {

            @Override
            public int height() {
                return row.height();
            }

            @Override
            public void layout(int x, int y) {
                row.layout(x, y);

                if (middle instanceof EditBox) middle.setY(y + 1);
            }

            @Override
            public void tick() {
                int offset = getOffset(coord, component);

                minus.setMessage(Component.literal(component.name() + " - " + offset));
                plus.setMessage(Component.literal(component.name() + " + " + offset));

                if (middle instanceof EditBox box && !box.isFocused()) {
                    String value = Integer.toString(getterVisual.getAsInt());

                    if (!box.getValue().equals(value)) box.setValue(value);
                }
            }
        };
    }

    private int getOffset(Coord coord, CoordComponent component) {
        if (Screen.hasShiftDown()) return 10;

        if (coord != Coord.Stack && Screen.hasControlDown()) {
            MMState state = getState();

            if (state == null) return 1;

            VoxelAABB deltas = coord == Coord.Paste ? state.config.getPasteVisualDeltas(null, false) :
                    state.config.getCopyVisualDeltas(null);

            Vector3i size = deltas == null ? new Vector3i(1, 1, 1) : deltas.size();

            return component.get(size);
        }

        return 1;
    }

    private Vector3i getTransformLocation(Coord coord, MMState currState) {
        Vector3i loc = switch (coord) {
            case Copy -> new Vector3i(0);
            case CopyA -> currState.config.coordA == null ? null : currState.config.coordA.toVec();
            case CopyB -> currState.config.coordB == null ? null : currState.config.coordB.toVec();
            case Paste -> currState.config.coordC == null ? null : currState.config.coordC.toVec();
            case Stack -> currState.config.arraySpan == null ? null : new Vector3i(currState.config.arraySpan);
        };

        if (loc == null) {
            if (coord == Coord.Stack) {
                loc = new Vector3i(0);
            } else {
                loc = MMUtils.getLookingAtLocation(player());
            }
        }

        return loc;
    }

    private IntSupplier createGetter(Coord coord, CoordComponent component) {
        return () -> {
            MMState currState = getState();

            if (currState == null) return 0;

            return component.get(getTransformLocation(coord, currState));
        };
    }

    private IntConsumer createSetter(Coord coord, CoordComponent component) {
        boolean pin = false;
        MMState state = getState();

        if (state != null && state.config.placeMode == PlaceMode.GEOMETRY && state.config.shape == Shape.CYLINDER &&
                coord == Coord.CopyA && state.config.coordA != null && state.config.coordB != null) {
            Vector3i vecA = state.config.coordA.toVec();
            Vector3i vecB = MMState.pinToPlanes(vecA, state.config.coordB.toVec());

            switch (vecB.sub(vecA).minComponent()) {
                case 0 -> pin = component == CoordComponent.X;
                case 1 -> pin = component == CoordComponent.Y;
                case 2 -> pin = component == CoordComponent.Z;
            }
        }

        final boolean shouldPin = pin;

        return i -> {
            MMState currState = getState();

            if (currState == null) return;

            var world = player().level();

            Vector3i loc = getTransformLocation(coord, currState);

            component.set(loc, i);

            // Cylinder shape coords handling
            if (currState.config.placeMode == PlaceMode.GEOMETRY && currState.config.shape == Shape.CYLINDER) {
                switch (coord) {
                    case Copy -> {
                        if (currState.config.coordA != null) {
                            currState.config.coordA = new Location(world, currState.config.coordA.toVec().add(loc));
                        }
                        if (currState.config.coordB != null) {
                            currState.config.coordB = new Location(world, currState.config.coordB.toVec().add(loc));
                        }
                        if (currState.config.coordC != null) {
                            currState.config.coordC = new Location(world, currState.config.coordC.toVec().add(loc));
                        }
                    }
                    case CopyA -> {
                        if (currState.config.coordA == null || currState.config.coordB == null ||
                                currState.config.coordC == null) {
                            break;
                        }

                        Vector3i vecA = currState.config.coordA.toVec();
                        Vector3i vecB = currState.config.coordB.toVec();
                        Vector3i vecC = currState.config.coordC.toVec();

                        if (shouldPin) {
                            component.set(vecB, i);
                            component.set(vecC, component.get(new Vector3i(vecC).sub(vecA)) + i);
                        } else {
                            component.set(vecC, i);

                            if (Math.abs(component.get(loc) - component.get(vecB)) < 1) break;
                        }

                        currState.config.coordA = new Location(world, loc);
                        currState.config.coordB = new Location(world, vecB);
                        currState.config.coordC = new Location(world, vecC);
                    }
                    case CopyB -> {
                        if (currState.config.coordA == null) break;

                        Vector3i vecA = currState.config.coordA.toVec();

                        if (Math.abs(component.get(loc) - component.get(vecA)) < 1) break;

                        currState.config.coordB = new Location(world, loc);
                    }
                    case Paste -> currState.config.coordC = new Location(world, loc);
                    default -> {}
                }

                ItemMatterManipulator.setState(getStack(), currState);

                switch (coord) {
                    case Copy, CopyA -> {
                        if (currState.config.coordA != null)
                            Messages.SetA.sendToServer(currState.config.coordA.toVec());
                        if (currState.config.coordB != null)
                            Messages.SetB.sendToServer(currState.config.coordB.toVec());
                        if (currState.config.coordC != null)
                            Messages.SetC.sendToServer(currState.config.coordC.toVec());
                    }
                    case CopyB -> {
                        if (currState.config.coordB != null)
                            Messages.SetB.sendToServer(currState.config.coordB.toVec());
                    }
                    case Paste -> Messages.SetC.sendToServer(currState.config.coordC.toVec());
                    default -> {}
                }
            } else {
                switch (coord) {
                    case Copy -> {
                        if (currState.config.coordA != null) {
                            currState.config.coordA = new Location(world, currState.config.coordA.toVec().add(loc));
                        }

                        if (currState.config.coordB != null) {
                            currState.config.coordB = new Location(world, currState.config.coordB.toVec().add(loc));
                        }
                    }
                    case CopyA -> currState.config.coordA = new Location(world, loc);
                    case CopyB -> currState.config.coordB = new Location(world, loc);
                    case Paste -> currState.config.coordC = new Location(world, loc);
                    case Stack -> currState.config.arraySpan = loc;
                }

                ItemMatterManipulator.setState(getStack(), currState);

                switch (coord) {
                    case Copy -> {
                        if (currState.config.coordA != null)
                            Messages.SetA.sendToServer(currState.config.coordA.toVec());
                        if (currState.config.coordB != null)
                            Messages.SetB.sendToServer(currState.config.coordB.toVec());
                    }
                    case CopyA -> Messages.SetA.sendToServer(loc);
                    case CopyB -> Messages.SetB.sendToServer(loc);
                    case Paste -> Messages.SetC.sendToServer(loc);
                    case Stack -> Messages.SetArray.sendToServer(loc);
                }
            }
        };
    }

    private List<Row> editorGroup(String headerKey, Coord coord, CoordComponent... components) {
        List<Row> rows = new ArrayList<>();

        rows.add(header(I18n.get(headerKey)));
        rows.add(padding(2));

        for (CoordComponent component : components) {
            rows.add(makeCoordinateEditor(coord, component));
            rows.add(padding(2));
        }

        rows.add(padding(8));

        return rows;
    }

    private static final CoordComponent[] XYZ = CoordComponent.values();

    // #endregion

    // #region Modes

    private void buildCopyMode() {
        // left side: rotation buttons
        leftRows.add(widgetRow(
                28,
                Button.builder(Component.translatable("mm.transform.button.rotate_x-"),
                        b -> Transform.sendRotate(EAST, false)).size(62, 18).build(),
                Button.builder(Component.translatable("mm.transform.button.rotate_x+"),
                        b -> Transform.sendRotate(EAST, true)).size(62, 18).build()));
        leftRows.add(widgetRow(
                28,
                Button.builder(Component.translatable("mm.transform.button.rotate_y-"),
                        b -> Transform.sendRotate(UP, false)).size(62, 18).build(),
                Button.builder(Component.translatable("mm.transform.button.rotate_y+"),
                        b -> Transform.sendRotate(UP, true)).size(62, 18).build()));
        leftRows.add(widgetRow(
                28,
                Button.builder(Component.translatable("mm.transform.button.rotate_z-"),
                        b -> Transform.sendRotate(SOUTH, false)).size(62, 18).build(),
                Button.builder(Component.translatable("mm.transform.button.rotate_z+"),
                        b -> Transform.sendRotate(SOUTH, true)).size(62, 18).build()));
        leftRows.add(widgetRow(
                28,
                Button.builder(Component.translatable("mm.transform.button.flip_x"),
                        b -> Messages.ToggleTransformFlip.sendToServer(Transform.FLIP_X)).size(40, 18).build(),
                Button.builder(Component.translatable("mm.transform.button.flip_y"),
                        b -> Messages.ToggleTransformFlip.sendToServer(Transform.FLIP_Y)).size(40, 18).build(),
                Button.builder(Component.translatable("mm.transform.button.flip_z"),
                        b -> Messages.ToggleTransformFlip.sendToServer(Transform.FLIP_Z)).size(40, 18).build()));

        Button reset = Button
                .builder(Component.translatable("mm.transform.button.reset"),
                        b -> Messages.ResetTransform.sendToServer())
                .size(40, 18).build();
        addRenderableWidget(reset);

        leftRows.add(new Row() {

            int x, y;

            @Override
            public int height() {
                return 66;
            }

            @Override
            public void layout(int x, int y) {
                this.x = x;
                this.y = y;
                reset.setX(x + 90);
                reset.setY(y + 24);
            }

            @Override
            public void render(GuiGraphics graphics) {
                MMState currState = getState();

                if (currState == null) return;

                graphics.fill(x, y, x + 88, y + 66, 0xFF888888);
                graphics.fill(x + 2, y + 2, x + 86, y + 64, 0xFF111111);

                Transform t = currState.getTransform();

                List<String> flips = new ArrayList<>();

                if (t.flipX) flips.add("X");
                if (t.flipY) flips.add("Y");
                if (t.flipZ) flips.add("Z");

                String info = I18n.get(
                        "mm.transform.info",
                        flips.isEmpty() ? "None" : String.join(", ", flips),
                        MMUtils.getDirectionDisplayName(t.up),
                        MMUtils.getDirectionDisplayName(t.forward)).replace("\\n", "\n");

                int lineY = y + 4;

                for (String line : info.split("\n")) {
                    graphics.drawString(font, line, x + 5, lineY, 0xFFDDDDDD, false);
                    lineY += font.lineHeight;
                }

                DirectionDrawable.draw(graphics, t, x + 18, y + 50, 12);

                graphics.drawString(font, Component.literal("X+ ").withStyle(ChatFormatting.RED)
                        .append(Component.literal("Y+ ").withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("Z+").withStyle(ChatFormatting.BLUE)), x + 36, y + 46, 0xFFFFFFFF,
                        false);
            }
        });

        List<Row> primary = new ArrayList<>();
        primary.addAll(editorGroup("mm.transform.header.copy_a", Coord.CopyA, XYZ));
        primary.addAll(editorGroup("mm.transform.header.copy_b", Coord.CopyB, XYZ));
        primary.addAll(editorGroup("mm.transform.header.paste", Coord.Paste, XYZ));

        List<Row> secondary = new ArrayList<>();
        secondary.addAll(editorGroup("mm.transform.header.copy", Coord.Copy, XYZ));
        secondary.addAll(editorGroup("mm.transform.header.stacking", Coord.Stack, XYZ));

        addColumns(primary, secondary);
    }

    private void buildMoveMode() {
        List<Row> primary = new ArrayList<>();
        primary.addAll(editorGroup("mm.transform.header.copy", Coord.Copy, XYZ));
        primary.add(widgetRow(
                28,
                Button.builder(Component.translatable("mm.transform.button.swap"),
                        b -> Messages.SwapRegion.sendToServer()).size(130, 18).build()));
        primary.addAll(editorGroup("mm.transform.header.paste", Coord.Paste, XYZ));

        List<Row> secondary = new ArrayList<>();
        secondary.addAll(editorGroup("mm.transform.header.copy_a", Coord.CopyA, XYZ));
        secondary.addAll(editorGroup("mm.transform.header.copy_b", Coord.CopyB, XYZ));

        addColumns(primary, secondary);
    }

    private void buildGeomMode() {
        MMState state = getState();

        switch (state.config.shape) {
            case CUBE, SPHERE, LINE -> buildCubeShape();
            case CYLINDER -> buildCylinderShape();
        }
    }

    private void buildCubeShape() {
        MMState state = getState();

        List<Row> rows = new ArrayList<>();

        if (state.config.coordA != null && state.config.coordB != null) {
            rows.addAll(editorGroup("mm.transform.header.coord_a", Coord.CopyA, XYZ));
            rows.addAll(editorGroup("mm.transform.header.coord_b", Coord.CopyB, XYZ));
        }

        rows.addAll(editorGroup("mm.transform.header.move", Coord.Copy, XYZ));

        addColumns(rows, List.of());
    }

    private void buildLineShape() {
        MMState state = getState();

        List<Row> rows = new ArrayList<>();

        if (state.config.coordA != null && state.config.coordB != null) {
            CoordComponent component;

            Location coordA = state.config.coordA;
            Vector3i vecB = MMState.pinToAxes(coordA.toVec(), state.config.coordB.toVec());

            if (vecB.x != coordA.x) component = CoordComponent.X;
            else if (vecB.y != coordA.y) component = CoordComponent.Y;
            else component = CoordComponent.Z;

            // Ensure coordB
            state.config.coordB = new Location(player().level(), vecB);
            ItemMatterManipulator.setState(getStack(), state);
            Messages.SetB.sendToServer(vecB);

            rows.addAll(editorGroup("mm.transform.header.coord_a", Coord.CopyA, component));
            rows.addAll(editorGroup("mm.transform.header.coord_b", Coord.CopyB, component));
        }

        rows.addAll(editorGroup("mm.transform.header.move", Coord.Copy, XYZ));

        addColumns(rows, List.of());
    }

    private void buildCylinderShape() {
        MMState state = getState();

        List<Row> rows = new ArrayList<>();

        if (state.config.coordA != null && state.config.coordB != null) {
            Location coordA = state.config.coordA;
            Vector3i vecB = MMState.pinToPlanes(coordA.toVec(), state.config.coordB.toVec());

            SortedSet<CoordComponent> bComponentsSet = new TreeSet<>(List.of(CoordComponent.values()));

            CoordComponent cComponent;

            if (vecB.x == coordA.x) {
                bComponentsSet.remove(CoordComponent.X);
                cComponent = CoordComponent.X;
            } else if (vecB.y == coordA.y) {
                bComponentsSet.remove(CoordComponent.Y);
                cComponent = CoordComponent.Y;
            } else {
                bComponentsSet.remove(CoordComponent.Z);
                cComponent = CoordComponent.Z;
            }

            CoordComponent[] bComponents = bComponentsSet.toArray(new CoordComponent[2]);

            Vector3i abDist = coordA.toVec().sub(vecB).absolute();
            boolean isValid = bComponents[0].get(abDist) >= 1 && bComponents[1].get(abDist) >= 1;

            if (isValid) {
                Vector3i vecC;

                if (state.config.coordC == null) {
                    vecC = coordA.toVec();
                    cComponent.set(vecC, cComponent.get(state.config.coordB.toVec()));
                } else {
                    vecC = MMState.pinToLine(coordA.toVec(), vecB, state.config.coordC.toVec());
                }

                // Ensure coords
                state.config.coordB = new Location(player().level(), vecB);
                state.config.coordC = new Location(player().level(), vecC);
                ItemMatterManipulator.setState(getStack(), state);
                Messages.SetB.sendToServer(vecB);
                Messages.SetC.sendToServer(vecC);

                rows.addAll(editorGroup("mm.transform.header.coord_a", Coord.CopyA, XYZ));
                rows.addAll(editorGroup("mm.transform.header.coord_b", Coord.CopyB, bComponents));
                rows.addAll(editorGroup("mm.transform.header.coord_c", Coord.Paste, cComponent));
            }
        }

        rows.addAll(editorGroup("mm.transform.header.move", Coord.Copy, XYZ));

        addColumns(rows, List.of());
    }

    // #endregion

    @Override
    public void tick() {
        super.tick();

        if (getState() == null) {
            onClose();
            return;
        }

        for (List<Row> column : columns) {
            for (Row row : column) row.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // transparent background, like the original TransparentModularGui
        for (List<Row> column : columns) {
            for (Row row : column) row.render(graphics);
        }

        for (Row row : leftRows) row.render(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
