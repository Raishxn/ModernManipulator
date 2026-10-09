package com.raishxn.modern_manipulator.common.building;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;

import com.google.gson.annotations.SerializedName;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * A block (as a full block state) or an item that can be placed by the manipulator.
 * This replaces the old (block id, metadata, CopyableProperty map) triple: the block state contains every property.
 */
public class BlockSpec implements ImmutableBlockSpec {

    @SerializedName("b")
    private boolean isBlock;

    /** The block state string (for blocks) */
    @SerializedName("s")
    private String blockState;

    /** The item id (for items) */
    @SerializedName("id")
    private String objectId;

    /** The item tag (for items) */
    @SerializedName("t")
    private CompoundTag itemTag;

    private transient BlockState state;
    private transient Item item;
    private transient ItemStack stack;

    public BlockSpec() {
        reset();
    }

    private BlockSpec reset() {
        isBlock = true;
        blockState = null;
        objectId = null;
        itemTag = null;
        // lazily parsed from blockState (gson uses this constructor too)
        state = null;
        item = null;
        stack = null;

        return this;
    }

    public BlockSpec setObject(BlockState state) {
        reset();
        this.isBlock = true;
        this.state = state;
        this.blockState = state.isAir() ? null : BlockStateParser.serialize(state);

        return this;
    }

    public BlockSpec setObject(ItemStack stack) {
        reset();
        this.isBlock = false;
        this.objectId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        this.itemTag = stack.getTag() == null || stack.getTag().isEmpty() ? null : stack.getTag().copy();
        this.state = null;

        return this;
    }

    @Override
    public boolean isBlockSpec() {
        return isBlock;
    }

    private void populate() {
        if (isBlock) {
            if (state == null) {
                state = parseState(blockState);
            }
        } else {
            if (item == null) {
                item = objectId == null ? Items.AIR : BuiltInRegistries.ITEM.get(new ResourceLocation(objectId));
            }

            if (state == null) {
                Block block = item instanceof BlockItem blockItem ? blockItem.getBlock() : Blocks.AIR;

                // AE parts are placed into a cable bus
                if (block == Blocks.AIR && ItemSpecHelpers.isAEPart(item)) {
                    block = InteropConstants.getAECableBus();
                }

                state = block.defaultBlockState();
            }
        }
    }

    private static BlockState parseState(@Nullable String text) {
        if (text == null || text.isEmpty()) return Blocks.AIR.defaultBlockState();

        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState();
        } catch (Exception e) {
            ModernManipulator.LOG.warn("Could not parse block state '{}'", text, e);
            return Blocks.AIR.defaultBlockState();
        }
    }

    @Override
    public @NotNull Block getBlock() {
        return getBlockState().getBlock();
    }

    @Override
    public @NotNull BlockState getBlockState() {
        if (state == null) populate();

        return state;
    }

    @Override
    public @NotNull Item getItem() {
        ItemStack stack = toStack(1);

        return stack.isEmpty() ? Items.AIR : stack.getItem();
    }

    @Override
    public ItemStack toStack(int amount) {
        if (this.stack == null) {
            ItemStack stack;

            if (isBlock) {
                Item blockItem = getBlock().asItem();

                stack = blockItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(blockItem, 1);

                // slabs need two items when doubled
                if (!stack.isEmpty() && getBlockState().hasProperty(BlockStateProperties.SLAB_TYPE) &&
                        getBlockState().getValue(BlockStateProperties.SLAB_TYPE) ==
                                net.minecraft.world.level.block.state.properties.SlabType.DOUBLE) {
                    stack.setCount(2);
                }
            } else {
                if (item == null) populate();

                stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, 1);

                if (!stack.isEmpty() && itemTag != null) stack.setTag(itemTag.copy());
            }

            this.stack = stack;
        }

        if (this.stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack out = this.stack.copy();
        out.setCount(out.getCount() * amount);
        return out;
    }

    @Override
    public PendingBlock instantiate(String worldId, int x, int y, int z) {
        return new PendingBlock(worldId, x, y, z, this);
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public BlockSpec clone() {
        BlockSpec dup = new BlockSpec();

        dup.isBlock = isBlock;
        dup.blockState = blockState;
        dup.objectId = objectId;
        dup.itemTag = itemTag == null ? null : itemTag.copy();
        dup.state = state;
        dup.item = item;
        dup.stack = stack;

        return dup;
    }

    @Override
    public ImmutableBlockSpec transform(Transform transform) {
        if (!isBlock) return this;

        BlockState transformed = BlockStateTransformer.transform(getBlockState(), transform);

        if (transformed == getBlockState()) return this;

        return withBlockState(transformed);
    }

    @Override
    public BlockSpec withBlockState(BlockState state) {
        if (!isBlock) return this;

        return new BlockSpec().setObject(state);
    }

    @Override
    public String getDisplayName() {
        return getChatComponent().getString();
    }

    @Override
    public MutableComponent getChatComponent() {
        ItemStack stack = toStack(1);

        if (stack.isEmpty()) {
            return getBlock().getName();
        } else {
            return stack.getHoverName().copy();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof BlockSpec other)) return false;

        if (isBlock != other.isBlock) return false;

        if (isBlock) {
            return getBlockState() == other.getBlockState();
        } else {
            return Objects.equals(objectId, other.objectId) && Objects.equals(itemTag, other.itemTag);
        }
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(isBlock);

        if (isBlock) {
            result = 31 * result + getBlockState().hashCode();
        } else {
            result = 31 * result + Objects.hashCode(objectId);
            result = 31 * result + Objects.hashCode(itemTag);
        }

        return result;
    }

    @Override
    public String toString() {
        return "BlockSpec [isBlock=" + isBlock + ", state=" + blockState + ", objectId=" + objectId + ", tag=" +
                itemTag + "]";
    }

    public String toDisplayString() {
        return getDisplayName();
    }

    public static BlockSpec fromPickBlock(Level world, Player player, @Nullable BlockHitResult hit) {
        if (hit == null) return new BlockSpec();

        return fromBlock(null, world, hit.getBlockPos());
    }

    public static BlockSpec fromBlock(@Nullable BlockSpec pooled, Level world, int x, int y, int z) {
        return fromBlock(pooled, world, new BlockPos(x, y, z));
    }

    public static BlockSpec fromBlock(@Nullable BlockSpec pooled, Level world, BlockPos pos) {
        return fromBlockState(pooled, world.getBlockState(pos));
    }

    public static BlockSpec fromBlockState(@Nullable BlockSpec pooled, BlockState state) {
        BlockSpec spec = pooled != null ? pooled.reset() : new BlockSpec();

        if (!InteropConstants.isFree(state) && state.getBlock().asItem() == Items.AIR) {
            // this block can't be represented by an item, so it can't be placed
            return spec;
        }

        spec.setObject(state);

        return spec;
    }

    public static BlockSpec fromStack(@Nullable BlockSpec pooled, ItemStack stack) {
        // Convert a stack to a BlockSpec as obtained from an in-world pick.
        if (stack == null || stack.isEmpty()) return pooled != null ? pooled.reset() : air();

        BlockSpec spec = pooled != null ? pooled.reset() : new BlockSpec();

        // AE2 parts are placed as parts, not blocks: keep them item-based so that they stay
        // consistent with the world pick path and are recognized as cables.
        if (ItemSpecHelpers.isAEPart(stack.getItem())) return spec.setObject(stack);

        if (stack.getItem() instanceof BlockItem blockItem) {
            return spec.setObject(blockItem.getBlock().defaultBlockState());
        }

        return spec.setObject(stack);
    }

    /**
     * Checks if this state is the secondary half of a multi-block block (door tops, bed heads, etc).
     * These are placed automatically by their primary half.
     */
    public static boolean isSecondaryHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) &&
                state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }

        if (state.hasProperty(BlockStateProperties.BED_PART) &&
                state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) {
            return true;
        }

        return state.is(Blocks.PISTON_HEAD);
    }

    public static final ImmutableBlockSpec AIR = air();

    public static BlockSpec air() {
        return new BlockSpec();
    }

    public static Component nameOf(ImmutableBlockSpec spec) {
        return spec.getChatComponent();
    }
}
