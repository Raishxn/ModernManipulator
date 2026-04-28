package com.raishxn.modern_manipulator.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

public class MMState {

    public static final String TAG_NAME = "MMState";

    private static final String TAG_COORD_A = "coordA";
    private static final String TAG_COORD_B = "coordB";
    private static final String TAG_MODE = "mode";
    private static final String TAG_SHAPE = "shape";

    private @Nullable MarkedPosition coordA;
    private @Nullable MarkedPosition coordB;
    private ToolMode mode = ToolMode.GEOMETRY;
    private Shape shape = Shape.CUBE;

    public static MMState getOrCreate(CompoundTag root) {
        if (!root.contains(TAG_NAME, Tag.TAG_COMPOUND)) {
            MMState state = new MMState();
            root.put(TAG_NAME, state.save());
            return state;
        }
        return load(root.getCompound(TAG_NAME));
    }

    public static MMState load(CompoundTag tag) {
        MMState state = new MMState();
        if (tag.contains(TAG_COORD_A, Tag.TAG_COMPOUND)) {
            state.coordA = MarkedPosition.load(tag.getCompound(TAG_COORD_A));
        }
        if (tag.contains(TAG_COORD_B, Tag.TAG_COMPOUND)) {
            state.coordB = MarkedPosition.load(tag.getCompound(TAG_COORD_B));
        }
        state.mode = ToolMode.byName(tag.getString(TAG_MODE));
        state.shape = Shape.byName(tag.getString(TAG_SHAPE));
        return state;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (coordA != null) {
            tag.put(TAG_COORD_A, coordA.save());
        }
        if (coordB != null) {
            tag.put(TAG_COORD_B, coordB.save());
        }
        tag.putString(TAG_MODE, mode.serializedName);
        tag.putString(TAG_SHAPE, shape.serializedName);
        return tag;
    }

    public void saveInto(CompoundTag root) {
        root.put(TAG_NAME, save());
    }

    public @Nullable MarkedPosition coordA() {
        return coordA;
    }

    public @Nullable MarkedPosition coordB() {
        return coordB;
    }

    public ToolMode mode() {
        return mode;
    }

    public Shape shape() {
        return shape;
    }

    public void setCoordA(MarkedPosition coordA) {
        this.coordA = coordA;
    }

    public void setCoordB(MarkedPosition coordB) {
        this.coordB = coordB;
    }

    public record MarkedPosition(ResourceLocation dimension, BlockPos pos) {

        private static final String TAG_DIMENSION = "dimension";
        private static final String TAG_X = "x";
        private static final String TAG_Y = "y";
        private static final String TAG_Z = "z";

        public static MarkedPosition load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
            if (dimension == null) {
                dimension = Level.OVERWORLD.location();
            }
            BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            return new MarkedPosition(dimension, pos);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_DIMENSION, dimension.toString());
            tag.putInt(TAG_X, pos.getX());
            tag.putInt(TAG_Y, pos.getY());
            tag.putInt(TAG_Z, pos.getZ());
            return tag;
        }

        public String shortText() {
            return dimension + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
        }
    }

    public enum ToolMode {

        GEOMETRY("geometry"),
        COPYING("copying"),
        EXCHANGING("exchanging"),
        MOVING("moving"),
        CABLES("cables");

        private final String serializedName;

        ToolMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static ToolMode byName(String name) {
            for (ToolMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return GEOMETRY;
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.mode." + serializedName);
        }
    }

    public enum Shape {

        LINE("line"),
        CUBE("cube"),
        SPHERE("sphere"),
        CYLINDER("cylinder");

        private final String serializedName;

        Shape(String serializedName) {
            this.serializedName = serializedName;
        }

        private static Shape byName(String name) {
            for (Shape shape : values()) {
                if (shape.serializedName.equals(name)) {
                    return shape;
                }
            }
            return CUBE;
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.shape." + serializedName);
        }
    }
}
