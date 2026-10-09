package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.raishxn.modern_manipulator.common.items.manipulator.Transform;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

public interface ImmutableBlockSpec {

    /** True when this spec represents a block (state), false when it represents an item (ae parts, etc). */
    boolean isBlockSpec();

    @NotNull
    Block getBlock();

    /** The block state that should be placed. */
    @NotNull
    BlockState getBlockState();

    /** The item for this spec (may be air). */
    @NotNull
    Item getItem();

    /** The item stack for this spec (may be empty if this spec can't be represented by an item). */
    ItemStack toStack(int amount);

    PendingBlock instantiate(String worldId, int x, int y, int z);

    default PendingBlock instantiate(Level world, int x, int y, int z) {
        return instantiate(world.dimension().location().toString(), x, y, z);
    }

    /** Returns a copy of this spec with the transform applied to its block state. */
    ImmutableBlockSpec transform(Transform transform);

    /** Returns a copy of this spec with the given block state. */
    ImmutableBlockSpec withBlockState(BlockState state);

    default boolean isEquivalent(ImmutableBlockSpec other) {
        if (other == null) return false;

        ItemStack a = toStack(1);
        ItemStack b = other.toStack(1);

        if (a.isEmpty() && b.isEmpty()) return getBlock() == other.getBlock();

        return ItemStack.isSameItemSameTags(a, b);
    }

    /** Returns true when this contains air. BlockSpecs may be air if an invalid block was analyzed. */
    default boolean isAir() {
        return InteropConstants.isAir(getBlockState());
    }

    default boolean skipWhenCopying() {
        return InteropConstants.skipWhenCopying(getBlockState());
    }

    default boolean shouldDropItem() {
        return InteropConstants.shouldDropItem(getBlockState());
    }

    default boolean isFree() {
        return InteropConstants.isFree(getBlockState());
    }

    String getDisplayName();

    MutableComponent getChatComponent();

    static Comparator<ImmutableBlockSpec> getComparator() {
        return Comparator.comparing(s -> s.toStack(1), (a, b) -> {
            if (a.isEmpty() && !b.isEmpty()) return -1;
            if (!a.isEmpty() && b.isEmpty()) return 1;
            if (a.isEmpty()) return 0;

            int result;

            result = String.CASE_INSENSITIVE_ORDER
                    .compare(BuiltInRegistries.ITEM.getKey(a.getItem()).toString(),
                            BuiltInRegistries.ITEM.getKey(b.getItem()).toString());
            if (result != 0) return result;

            CompoundTag ta = a.getTag();
            CompoundTag tb = b.getTag();

            if (ta == null && tb != null) return -1;
            if (ta != null && tb == null) return 1;
            if (ta == null) return 0;

            return Integer.compare(ta.hashCode(), tb.hashCode());
        });
    }
}
