package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.building.providers.IItemProvider;
import com.raishxn.modern_manipulator.common.compat.TileAnalyzers;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * This represents a block in the world.
 * It stores everything the building algorithm needs to know to place a block.
 */
public class PendingBlock extends Location {

    public ImmutableBlockSpec spec;

    public ITileAnalysisIntegration gt;
    public ITileAnalysisIntegration ae;
    public ITileAnalysisIntegration nbt;
    public transient SmartCopyIntegration smartCopy;

    public InventoryAnalysis inventory = null;

    public int renderOrder, buildOrder;

    public PendingBlock(String worldId, int x, int y, int z, @NotNull ImmutableBlockSpec spec) {
        super(worldId, x, y, z);
        this.spec = spec;
    }

    private PendingBlock() {}

    /**
     * Clears this block's info but not its position.
     */
    public PendingBlock reset() {
        this.spec = null;
        this.gt = null;
        this.ae = null;
        this.nbt = null;
        this.smartCopy = null;
        this.inventory = null;
        this.renderOrder = 0;
        this.buildOrder = 0;

        return this;
    }

    public PendingBlock setBlock(ImmutableBlockSpec spec) {
        reset();

        this.spec = spec;

        return this;
    }

    public PendingBlock setBlock(BlockState state) {
        reset();

        this.spec = new BlockSpec().setObject(state);

        return this;
    }

    public PendingBlock setBlock(ItemStack stack) {
        reset();

        this.spec = new BlockSpec().setObject(stack);

        return this;
    }

    public PendingBlock setOrders(int renderOrder, int buildOrder) {
        this.renderOrder = renderOrder;
        this.buildOrder = buildOrder;

        return this;
    }

    public Block getBlock() {
        return spec.getBlock();
    }

    public BlockState getBlockState() {
        return spec.getBlockState();
    }

    public BlockState getPreviewState() {
        for (var integration : getIntegrations()) {
            BlockState preview = integration.getPreviewState();
            if (preview != null) return preview;
        }

        return spec.getBlockState();
    }

    /** Blocks that look better previewed as their item (pipes, cables). Registered by integrations. */
    public static final List<java.util.function.Predicate<BlockState>> ITEM_PREVIEW_BLOCKS = new ArrayList<>();

    /**
     * Gets the item that should be shown in the preview instead of the block, or null to show the block.
     */
    public ItemStack getPreviewStack() {
        for (var integration : getIntegrations()) {
            ItemStack preview = integration.getPreviewStack();
            if (preview != null && !preview.isEmpty()) return preview;
        }

        if (!spec.isBlockSpec()) {
            ItemStack stack = spec.toStack(1);
            if (!stack.isEmpty()) return stack;
        }

        for (var predicate : ITEM_PREVIEW_BLOCKS) {
            if (predicate.test(spec.getBlockState())) {
                ItemStack stack = spec.toStack(1);
                if (!stack.isEmpty()) return stack;
            }
        }

        return null;
    }

    public Item getItem() {
        return spec.getItem();
    }

    public List<ITileAnalysisIntegration> getIntegrations() {
        List<ITileAnalysisIntegration> list = new ArrayList<>(3);

        if (gt != null) list.add(gt);
        if (ae != null) list.add(ae);
        if (nbt != null) list.add(nbt);
        if (smartCopy != null) list.add(smartCopy);

        return list;
    }

    private Component getItemDetailsChat() {
        List<Component> details = new ArrayList<>(0);

        for (var analysis : getIntegrations()) {
            analysis.getItemDetailsChat(details);
        }

        if (details.isEmpty()) return Component.empty();

        MutableComponent out = details.get(0).copy();
        for (int i = 1; i < details.size(); i++) {
            out.append(", ").append(details.get(i));
        }

        return Component.literal(" (").append(out).append(")");
    }

    public ItemStack getStack() {
        ItemStack stack = spec.toStack(1);

        if (stack.isEmpty()) return stack;

        for (var analysis : getIntegrations()) {
            analysis.getItemTag(stack);
        }

        return stack;
    }

    /**
     * Get the required items for a block that exists in the world
     *
     * @return True when this result can be applied to the tile, false otherwise
     */
    public boolean getRequiredItemsForExistingBlock(IBlockApplyContext context) {
        BlockEntity te = context.getTileEntity();

        for (var analysis : getIntegrations()) {
            if (!analysis.getRequiredItemsForExistingBlock(context)) return false;
        }

        if (this.inventory != null && te != null) {
            this.inventory.apply(context, te, true, true);
        }

        return true;
    }

    /**
     * Get the required items for a block that doesn't exist
     *
     * @return True if this tile result is valid, false otherwise
     */
    public boolean getRequiredItemsForNewBlock(IBlockApplyContext context) {
        for (var analysis : getIntegrations()) {
            if (!analysis.getRequiredItemsForNewBlock(context)) return false;
        }

        if (this.inventory != null) {
            for (IItemProvider item : this.inventory.mItems) {
                if (item != null) {
                    item.getStack(context, true);
                }
            }
        }

        return true;
    }

    public MutableComponent getDisplayNameChat() {
        ItemStack stack = getStack();

        MutableComponent name = stack.isEmpty() ? spec.getChatComponent() : stack.getHoverName().copy();

        return name.append(getItemDetailsChat());
    }

    public boolean isFree() {
        return spec.isFree();
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public PendingBlock clone() {
        PendingBlock dup = new PendingBlock();

        dup.worldId = worldId;
        dup.x = x;
        dup.y = y;
        dup.z = z;
        dup.spec = spec;
        if (gt != null) dup.gt = gt.clone();
        if (ae != null) dup.ae = ae.clone();
        if (nbt != null) dup.nbt = nbt.clone();
        if (smartCopy != null) dup.smartCopy = smartCopy.clone();
        if (inventory != null) dup.inventory = inventory.clone();
        dup.renderOrder = renderOrder;
        dup.buildOrder = buildOrder;

        return dup;
    }

    public void transform(Transform transform) {
        spec = spec.transform(transform);

        for (var analysis : getIntegrations()) {
            analysis.transform(transform);
        }
    }

    /**
     * Applies this block's configuration to the block that exists in the world.
     *
     * @return True if something was changed
     */
    public boolean apply(IBlockApplyContext context, Level world) {
        boolean didSomething = false;

        BlockPos pos = new BlockPos(x, y, z);
        BlockState existing = world.getBlockState(pos);
        BlockState target = spec.getBlockState();

        // the equivalent of the old 'copyable properties': update the block state if the block is the same
        if (spec.isBlockSpec() && existing.getBlock() == target.getBlock() && existing != target) {
            BlockState updated = Block.updateFromNeighbourShapes(target, world, pos);

            if (updated != existing) {
                world.setBlock(pos, updated, Block.UPDATE_ALL);
                didSomething = true;
            }
        }

        BlockCaptureDrops.captureDrops(world);

        boolean success = true;

        try {
            for (var analysis : getIntegrations()) {
                if (!analysis.apply(context)) {
                    success = false;
                    break;
                }
            }

            BlockEntity te = context.getTileEntity();

            if (success && te != null && this.inventory != null) {
                if (!this.inventory.apply(context, te, true, false)) {
                    success = false;
                }
            }
        } finally {
            context.givePlayerItems(BlockCaptureDrops.stopCapturingDrops(world).toArray(new ItemStack[0]));

            BlockState state = world.getBlockState(pos);
            world.updateNeighborsAt(pos, state.getBlock());
            world.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }

        return success && didSomething;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), spec, gt, ae, nbt, inventory, renderOrder, buildOrder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        PendingBlock other = (PendingBlock) obj;
        return Objects.equals(spec, other.spec) && Objects.equals(gt, other.gt) && Objects.equals(ae, other.ae) &&
            Objects.equals(nbt, other.nbt) && Objects.equals(inventory, other.inventory) && renderOrder == other.renderOrder &&
            buildOrder == other.buildOrder;
    }

    /**
     * A comparator for sorting blocks prior to building.
     */
    public static Comparator<PendingBlock> getComparator() {
        return Comparator.comparingInt((PendingBlock b) -> b.buildOrder)
            .thenComparing(b -> b.spec, ImmutableBlockSpec.getComparator())
            .thenComparingInt(b -> b.x >> 4)
            .thenComparingInt(b -> b.z >> 4)
            .thenComparingLong(value -> BlockPos.asLong(value.x, value.y, value.z));
    }

    public static PendingBlock fromBlock(Level world, int x, int y, int z) {
        return BlockSpec.fromBlock(null, world, x, y, z).instantiate(world, x, y, z);
    }

    private static int counter = 0;
    public static final int ANALYZE_GT = 0b1 << counter++;
    public static final int ANALYZE_AE = 0b1 << counter++;
    public static final int ANALYZE_NBT = 0b1 << counter++;
    public static final int ANALYZE_INV = 0b1 << counter++;
    public static final int ANALYZE_ALL = -1;

    public PendingBlock analyze(BlockEntity te, int flags) {
        if (te != null) {
            TileAnalyzers.analyze(this, te, flags);

            if ((flags & ANALYZE_INV) != 0) {
                this.inventory = InventoryAnalysis.fromInventory(te, false);
            }
        }

        return this;
    }

    public PendingBlock migrate() {
        for (var integration : getIntegrations()) {
            integration.migrate();
        }

        return this;
    }

    public static boolean areEquivalent(PendingBlock a, PendingBlock b) {
        ItemStack sa = a.getStack();
        ItemStack sb = b.getStack();

        if (sa.isEmpty() && sb.isEmpty()) {
            return a.spec.equals(b.spec);
        } else {
            return ItemStack.isSameItemSameTags(sa, sb);
        }
    }
}
