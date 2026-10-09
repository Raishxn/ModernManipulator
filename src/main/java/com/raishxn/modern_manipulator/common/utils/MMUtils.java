package com.raishxn.modern_manipulator.common.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.raishxn.modern_manipulator.common.building.IPseudoInventory;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

public class MMUtils {

    /**
     * Formats a number with group separator and at most 2 fraction digits.
     */
    private static final DecimalFormat DECIMAL_FORMAT;

    static {
        DECIMAL_FORMAT = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    }

    /** When set in the tooltip response, the ME system could be reached. */
    public static final int TOOLTIP_AE_WORKS = 0b1;
    /** When set in the tooltip response, the uplink could be reached. */
    public static final int TOOLTIP_UPLINK_WORKS = 0b10;

    /** When set, the plan will be submitted to the uplink's AE system automatically */
    public static final int PLAN_AUTO_SUBMIT = 0b1;
    /** When set, every item will be requested, even the ones the player already has */
    public static final int PLAN_ALL = 0b10;

    private MMUtils() {}

    public static int clamp(int val, int lo, int hi) {
        return val < lo ? lo : val > hi ? hi : val;
    }

    public static long clamp(long val, long lo, long hi) {
        return val < lo ? lo : val > hi ? hi : val;
    }

    public static int min(int first, int... rest) {
        for (int j : rest) {
            if (j < first) first = j;
        }
        return first;
    }

    public static int max(int first, int... rest) {
        for (int j : rest) {
            if (j > first) first = j;
        }
        return first;
    }

    public static int ceilDiv(int lhs, int rhs) {
        return (lhs + rhs - 1) / rhs;
    }

    public static long ceilDiv(long lhs, long rhs) {
        return (lhs + rhs - 1) / rhs;
    }

    public static int signum(int x) {
        return Integer.compare(x, 0);
    }

    public static Vector3i signum(Vector3i v) {
        v.x = signum(v.x);
        v.y = signum(v.y);
        v.z = signum(v.z);
        return v;
    }

    public static long ceilLong(double d) {
        long l = (long) d;
        return d > l ? l + 1 : l;
    }

    public static String formatNumber(long number) {
        synchronized (DECIMAL_FORMAT) {
            return DECIMAL_FORMAT.format(number);
        }
    }

    public static String formatNumber(double number) {
        synchronized (DECIMAL_FORMAT) {
            return DECIMAL_FORMAT.format(number);
        }
    }

    // #region Raytracing

    public static double getBlockReachDistance(Player player) {
        // The server's base reach is 5 in both modes; the client uses 4.5 in survival.
        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());

        if (player instanceof ServerPlayer && !player.isCreative()) reach -= 0.5;

        return reach;
    }

    /**
     * Gets the standard vanilla hit result for a player.
     */
    public static @Nullable BlockHitResult getHitResult(Player player, boolean includeLiquids) {
        double reachDistance = getBlockReachDistance(player);

        Vec3 posVec = player.getEyePosition(1);
        Vec3 lookVec = player.getViewVector(1);
        Vec3 modifiedPosVec = posVec.add(lookVec.scale(reachDistance));

        BlockHitResult hit = player.level()
                .clip(
                        new ClipContext(
                                posVec,
                                modifiedPosVec,
                                ClipContext.Block.OUTLINE,
                                includeLiquids ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE,
                                player));

        return hit.getType() != HitResult.Type.BLOCK ? null : hit;
    }

    /**
     * Gets the 'location' that the player is looking at.
     */
    public static Vector3i getLookingAtLocation(Player player) {
        double dist = getBlockReachDistance(player);

        Vec3 start = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);
        Vec3 end = start.add(look.scale(dist));

        BlockHitResult hit = player.level()
                .clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        Vector3i target;

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            target = new Vector3i(pos.getX(), pos.getY(), pos.getZ());

            if (!player.isShiftKeyDown()) {
                Direction dir = hit.getDirection();
                target.add(dir.getStepX(), dir.getStepY(), dir.getStepZ());
            }
        } else {
            target = new Vector3i(Mth.floor(end.x), Mth.floor(end.y), Mth.floor(end.z));
        }

        return target;
    }

    // #endregion

    // #region Regions

    /**
     * Calculates the delta x/y/z for the bounding box around a,b.
     * This is useful because a,a + deltas will always represent the same bounding box that's around a,b.
     */
    public static Vector3i getRegionDeltas(Location a, Location b) {
        if (!Location.areCompatible(a, b)) return null;

        int x1 = a.x;
        int y1 = a.y;
        int z1 = a.z;
        int x2 = b.x;
        int y2 = b.y;
        int z2 = b.z;

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);

        int dX = (maxX - minX) * (minX < x1 ? -1 : 1);
        int dY = (maxY - minY) * (minY < y1 ? -1 : 1);
        int dZ = (maxZ - minZ) * (minZ < z1 ? -1 : 1);

        return new Vector3i(dX, dY, dZ);
    }

    /**
     * {@link #getRegionDeltas(Location, Location)} but with three params.
     */
    public static Vector3i getRegionDeltas(Location a, Location b, Location c) {
        if (!Location.areCompatible(a, b, c)) return null;

        Vector3i vA = a.toVec();
        Vector3i vB = b.toVec();
        Vector3i vC = c.toVec();

        Vector3i max = new Vector3i(vA).max(vB).max(vC);
        Vector3i min = new Vector3i(vA).min(vB).min(vC);

        int dX = (max.x - min.x) * (min.x < a.x ? -1 : 1);
        int dY = (max.y - min.y) * (min.y < a.y ? -1 : 1);
        int dZ = (max.z - min.z) * (min.z < a.z ? -1 : 1);

        return new Vector3i(dX, dY, dZ);
    }

    /**
     * Converts deltas to an AABB.
     */
    public static AABB getBoundingBox(Location l, Vector3i deltas) {
        int minX = Math.min(l.x, l.x + deltas.x);
        int minY = Math.min(l.y, l.y + deltas.y);
        int minZ = Math.min(l.z, l.z + deltas.z);
        int maxX = Math.max(l.x, l.x + deltas.x) + 1;
        int maxY = Math.max(l.y, l.y + deltas.y) + 1;
        int maxZ = Math.max(l.z, l.z + deltas.z) + 1;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Gets all blocks contained in a bounding box.
     */
    public static List<Vector3i> getBlocksInBB(Location l, Vector3i deltas) {
        int minX = Math.min(l.x, l.x + deltas.x);
        int minY = Math.min(l.y, l.y + deltas.y);
        int minZ = Math.min(l.z, l.z + deltas.z);
        int maxX = Math.max(l.x, l.x + deltas.x) + 1;
        int maxY = Math.max(l.y, l.y + deltas.y) + 1;
        int maxZ = Math.max(l.z, l.z + deltas.z) + 1;

        int dX = maxX - minX;
        int dY = maxY - minY;
        int dZ = maxZ - minZ;

        List<Vector3i> blocks = new ArrayList<>();

        for (int y = 0; y < dY; y++) {
            for (int z = 0; z < dZ; z++) {
                for (int x = 0; x < dX; x++) {
                    blocks.add(new Vector3i(minX + x, minY + y, minZ + z));
                }
            }
        }

        return blocks;
    }

    /**
     * Iterates over all array copy offsets for the given span and deltas,
     * calling the consumer with each offset vector (dx, dy, dz) in world-space
     * (before any transform is applied).
     */
    public static void forEachArrayOffset(Vector3i arraySpan, Vector3i deltas, Consumer<Vector3i> consumer) {
        int sx = arraySpan.x;
        int sy = arraySpan.y;
        int sz = arraySpan.z;

        for (int ay = Math.min(sy, 0); ay <= Math.max(sy, 0); ay++) {
            for (int az = Math.min(sz, 0); az <= Math.max(sz, 0); az++) {
                for (int ax = Math.min(sx, 0); ax <= Math.max(sx, 0); ax++) {
                    int dx = ax * (deltas.x + (deltas.x < 0 ? -1 : 1));
                    int dy = ay * (deltas.y + (deltas.y < 0 ? -1 : 1));
                    int dz = az * (deltas.z + (deltas.z < 0 ? -1 : 1));

                    consumer.accept(new Vector3i(dx, dy, dz));
                }
            }
        }
    }

    // #endregion

    // #region Chat

    private static Object[] wrapArgs(Object... args) {
        Object[] out = new Object[args.length];

        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];

            if (arg instanceof Component) {
                out[i] = arg;
            } else if (arg instanceof Long || arg instanceof Integer) {
                out[i] = formatNumber(((Number) arg).longValue());
            } else {
                out[i] = String.valueOf(arg);
            }
        }

        return out;
    }

    public static void sendChatToPlayerWithColor(Player player, MutableComponent chat, @Nullable ChatFormatting color) {
        if (player instanceof ServerPlayer && chat != null) {
            if (color != null) chat.withStyle(color);

            player.sendSystemMessage(chat);
        }
    }

    public static void sendErrorToPlayer(Player player, String key, Object... args) {
        sendChatToPlayerWithColor(player, Component.translatable(key, wrapArgs(args)), ChatFormatting.RED);
    }

    public static void sendWarningToPlayer(Player player, String key, Object... args) {
        sendChatToPlayerWithColor(player, Component.translatable(key, wrapArgs(args)), ChatFormatting.GOLD);
    }

    public static void sendInfoToPlayer(Player player, String key, Object... args) {
        sendChatToPlayerWithColor(player, Component.translatable(key, wrapArgs(args)), ChatFormatting.GRAY);
    }

    public static void sendInfoToPlayer(Player player, MutableComponent chat) {
        sendChatToPlayerWithColor(player, chat, ChatFormatting.GRAY);
    }

    public static void sendChatToPlayer(Player player, String key, Object... args) {
        sendChatToPlayerWithColor(player, Component.translatable(key, wrapArgs(args)), null);
    }

    // #endregion

    // #region Collections

    public static <S, T> List<T> mapToList(Collection<S> in, Function<S, T> mapper) {
        List<T> out = new ArrayList<>(in.size());

        for (S s : in) {
            T t = mapper.apply(s);
            if (t != null) out.add(t);
        }

        return out;
    }

    public static <S, T> List<T> mapToList(S[] in, Function<S, T> mapper) {
        List<T> out = new ArrayList<>(in.length);

        for (S s : in) {
            T t = mapper.apply(s);
            if (t != null) out.add(t);
        }

        return out;
    }

    public static <T> T getIndexSafe(T[] array, int index) {
        return array == null || index < 0 || index >= array.length ? null : array[index];
    }

    public static <T> T getIndexSafe(List<T> list, int index) {
        return list == null || index < 0 || index >= list.size() ? null : list.get(index);
    }

    public static <T> T choose(List<T> list, Random rng) {
        if (list.isEmpty()) return null;
        if (list.size() == 1) return list.get(0);

        return list.get(rng.nextInt(list.size()));
    }

    public static <K, V> boolean areMapsEqual(Map<K, V> left, Map<K, V> right) {
        if (left == null || left.isEmpty()) {
            return right == null || right.isEmpty();
        }

        if (right == null || right.isEmpty()) return false;

        return left.equals(right);
    }

    // #endregion

    // #region Items

    /**
     * Empties an inventory into the given pseudo inventory.
     */
    public static void emptyInventory(IPseudoInventory dest, IItemHandler src) {
        if (src == null) return;

        int size = src.getSlots();

        for (int i = 0; i < size; i++) {
            ItemStack stack = src.getStackInSlot(i);

            if (stack.isEmpty()) continue;

            if (src instanceof IItemHandlerModifiable modifiable) {
                dest.givePlayerItems(stack.copy());
                modifiable.setStackInSlot(i, ItemStack.EMPTY);
            } else {
                ItemStack extracted = src.extractItem(i, stack.getCount(), false);

                if (!extracted.isEmpty()) dest.givePlayerItems(extracted);
            }
        }
    }

    /**
     * Checks if two stacks are the same item with the same tag.
     */
    public static boolean areStacksBasicallyEqual(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() && b.isEmpty();

        return ItemStack.isSameItemSameTags(a, b);
    }

    public static ItemStack copyWithAmount(ItemStack stack, int amount) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack copy = stack.copy();
        copy.setCount(amount);
        return copy;
    }

    // #endregion

    // #region Directions

    public static String getDirectionDisplayName(@Nullable Direction dir) {
        return getDirectionDisplayName(dir, false);
    }

    public static String getDirectionDisplayName(@Nullable Direction dir, boolean unknownIsCentre) {
        return Component.translatable(getDirectionUnlocalizedName(dir, unknownIsCentre)).getString();
    }

    public static String getDirectionUnlocalizedName(@Nullable Direction dir, boolean unknownIsCentre) {
        if (dir == null) return unknownIsCentre ? "mm.direction.center" : "mm.direction.unknown";

        return switch (dir) {
            case DOWN -> "mm.direction.down";
            case UP -> "mm.direction.up";
            case NORTH -> "mm.direction.north";
            case SOUTH -> "mm.direction.south";
            case WEST -> "mm.direction.west";
            case EAST -> "mm.direction.east";
        };
    }

    public static @Nullable Direction parseDirection(@Nullable String name) {
        if (name == null) return null;

        return Direction.byName(name.toLowerCase(Locale.ROOT));
    }

    // #endregion

    public static boolean equalsAny(Object value, Object... options) {
        for (Object option : options) {
            if (Objects.equals(value, option)) return true;
        }

        return false;
    }
}
