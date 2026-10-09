package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import org.joml.Vector3f;

/**
 * Applies a manipulator {@link Transform} to a block state.
 * Replaces the old CopyableProperty transform logic (facing/forward/up/left/top/rotation/orientation).
 */
public class BlockStateTransformer {

    private BlockStateTransformer() {}

    public static BlockState transform(BlockState state, Transform transform) {
        if (transform == null || transform.isIdentity()) return state;

        Object[] horizontal = transform.getHorizontalDecomposition();

        if (horizontal != null) {
            // Vanilla handles every special case (stairs shapes, rails, walls, etc) for horizontal transforms
            return state.mirror((Mirror) horizontal[0]).rotate((Rotation) horizontal[1]);
        }

        return transformGeneric(state, transform);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState transformGeneric(BlockState state, Transform transform) {
        boolean flipsVertical = transform.apply(Direction.UP) == Direction.DOWN;

        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty dirProp) {
                Direction dir = state.getValue(dirProp);
                Direction newDir = transform.apply(dir);

                if (dirProp.getPossibleValues().contains(newDir)) {
                    state = state.setValue(dirProp, newDir);
                }

                continue;
            }

            if (property == BlockStateProperties.AXIS || property == BlockStateProperties.HORIZONTAL_AXIS) {
                EnumProperty<Direction.Axis> axisProp = (EnumProperty<Direction.Axis>) property;
                Direction.Axis axis = state.getValue(axisProp);
                Direction.Axis newAxis = transform.apply(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE))
                    .getAxis();

                if (axisProp.getPossibleValues().contains(newAxis)) {
                    state = state.setValue(axisProp, newAxis);
                }

                continue;
            }

            if (property == BlockStateProperties.ROTATION_16) {
                IntegerProperty rotProp = (IntegerProperty) property;
                int rotation = state.getValue(rotProp);

                Vector3f v = new Vector3f(0, 0, 1)
                    .rotateAxis(rotation * (float) Math.PI * 2f / 16f, 0, 1, 0)
                    .mulTransposeDirection(transform.getRotation());

                double degrees = Math.atan2(v.x, v.z) * 360d / Math.PI / 2d;
                int newRot = Mth.floor(degrees * 16d / 360d + 0.5);
                newRot = (newRot % 16 + 16) % 16;

                state = state.setValue(rotProp, newRot);
                continue;
            }

            if (flipsVertical) {
                if (property == BlockStateProperties.HALF) {
                    state = state.setValue(BlockStateProperties.HALF, state.getValue(BlockStateProperties.HALF) == Half.TOP ? Half.BOTTOM : Half.TOP);
                } else if (property == BlockStateProperties.SLAB_TYPE) {
                    SlabType type = state.getValue(BlockStateProperties.SLAB_TYPE);
                    if (type != SlabType.DOUBLE) {
                        state = state.setValue(BlockStateProperties.SLAB_TYPE, type == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP);
                    }
                } else if (property == BlockStateProperties.ATTACH_FACE) {
                    AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
                    if (face != AttachFace.WALL) {
                        state = state.setValue(BlockStateProperties.ATTACH_FACE, face == AttachFace.FLOOR ? AttachFace.CEILING : AttachFace.FLOOR);
                    }
                } else if (property instanceof EnumProperty enumProp && enumProp.getValueClass() == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.class) {
                    // never flip double block halves, the lower half places the upper half
                }
            }
        }

        return state;
    }
}
