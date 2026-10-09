package com.raishxn.modern_manipulator.common.items.manipulator;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import com.raishxn.modern_manipulator.common.items.manipulator.MMConfig.VoxelAABB;
import com.raishxn.modern_manipulator.common.networking.Messages;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3i;

import static net.minecraft.core.Direction.DOWN;
import static net.minecraft.core.Direction.EAST;
import static net.minecraft.core.Direction.NORTH;
import static net.minecraft.core.Direction.SOUTH;
import static net.minecraft.core.Direction.UP;
import static net.minecraft.core.Direction.WEST;

/**
 * Represents the rotation and flipping.
 */
public class Transform {

    public boolean flipX, flipY, flipZ;
    public Direction forward = NORTH, up = UP;

    public transient Matrix4f rotation;

    public static final int FLIP_X = 0b1, FLIP_Y = 0b10, FLIP_Z = 0b100, FORWARD_MASK = 0b111000, FORWARD_SHIFT = 3,
            UP_MASK = 0b111000000, UP_SHIFT = 6;

    public static void sendRotate(Direction dir, boolean positive) {
        Messages.RotateTransform.sendToServer((dir.ordinal() & 0xFF) | (positive ? 1 : 0) << 8);
    }

    public Matrix4f getRotation() {
        if (rotation != null) return rotation;

        Matrix4f flip = new Matrix4f();
        flip.scale(flipX ? -1 : 1, flipY ? -1 : 1, flipZ ? -1 : 1);

        Matrix4f rot = new Matrix4f().lookAlong(v(forward), v(up));

        return rot.mul(flip);
    }

    public void cacheRotation() {
        rotation = getRotation();
    }

    public void uncacheRotation() {
        rotation = null;
    }

    public boolean isIdentity() {
        return !flipX && !flipY && !flipZ && forward == NORTH && up == UP;
    }

    public Direction apply(@Nullable Direction dir) {
        if (dir == null) return null;

        return vprime(v(dir).mulTransposeDirection(getRotation()));
    }

    public byte applyBits(int bitmask) {
        if (bitmask == 0) return 0;

        int out = 0;

        for (Direction dir : Direction.values()) {
            if ((bitmask & (1 << dir.ordinal())) != 0) {
                out |= 1 << apply(dir).ordinal();
            }
        }

        return (byte) out;
    }

    public Vector3i apply(Vector3i v) {
        Vector3f v2 = new Vector3f(v).mulTransposeDirection(getRotation());

        v.x = Math.round(v2.x);
        v.y = Math.round(v2.y);
        v.z = Math.round(v2.z);

        return v;
    }

    public VoxelAABB apply(VoxelAABB bb) {
        bb.a.sub(bb.origin);
        apply(bb.a);
        bb.a.add(bb.origin);

        bb.b.sub(bb.origin);
        apply(bb.b);
        bb.b.add(bb.origin);

        return bb;
    }

    /**
     * Rotates this transform.
     *
     * @param dir    The axis to rotate around
     * @param amount The amount to rotate (1 = 90 degrees)
     */
    public void rotate(Direction dir, int amount) {
        rotation = null;
        Matrix4f rot = new Matrix4f().rotate((float) (Math.PI / 2 * amount), v(dir));

        up = transform(up, rot);
        forward = transform(forward, rot);
    }

    /**
     * Decomposes this transform into a vanilla mirror followed by a rotation around the Y axis, if possible.
     * This is only possible when the transform keeps the Y axis in place (no vertical rotations or Y flips).
     *
     * @return {mirror, rotation} or null when the transform isn't a horizontal transform
     */
    public Object @Nullable [] getHorizontalDecomposition() {
        if (apply(UP) != UP) return null;

        Direction north = apply(NORTH);
        Direction east = apply(EAST);

        for (Mirror mirror : new Mirror[] { Mirror.NONE, Mirror.FRONT_BACK }) {
            for (Rotation rot : Rotation.values()) {
                Direction n = rot.rotate(mirror.mirror(NORTH));
                Direction e = rot.rotate(mirror.mirror(EAST));

                if (n == north && e == east) return new Object[] { mirror, rot };
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return "Transform [flipX=" + flipX + ", flipY=" + flipY + ", flipZ=" + flipZ + ", forward=" + forward +
                ", up=" +
                up + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (flipX ? 1231 : 1237);
        result = prime * result + (flipY ? 1231 : 1237);
        result = prime * result + (flipZ ? 1231 : 1237);
        result = prime * result + ((forward == null) ? 0 : forward.hashCode());
        result = prime * result + ((up == null) ? 0 : up.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Transform other = (Transform) obj;
        if (flipX != other.flipX) return false;
        if (flipY != other.flipY) return false;
        if (flipZ != other.flipZ) return false;
        if (forward != other.forward) return false;
        return up == other.up;
    }

    public static Vector3f v(Direction dir) {
        return new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
    }

    public static Direction vprime(Vector3f dir) {
        return switch (dir.maxComponent()) {
            case 0 -> dir.x > 0 ? EAST : WEST;
            case 1 -> dir.y > 0 ? UP : DOWN;
            case 2 -> dir.z > 0 ? SOUTH : NORTH;
            default -> throw new AssertionError();
        };
    }

    public static Direction transform(Direction dir, Matrix4f transform) {
        return vprime(v(dir).mulTransposeDirection(transform));
    }
}
