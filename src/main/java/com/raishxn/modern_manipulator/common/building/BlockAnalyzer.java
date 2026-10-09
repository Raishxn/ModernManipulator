package com.raishxn.modern_manipulator.common.building;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendWarningToPlayer;

import com.raishxn.modern_manipulator.GlobalMMConfig.DebugConfig;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import com.raishxn.modern_manipulator.common.utils.FluidId;
import com.raishxn.modern_manipulator.common.utils.ItemId;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BlockAnalyzer {

    private BlockAnalyzer() {}

    /**
     * Analyzes a region.
     * The returned PendingBlocks are relative to zero.
     * The calling code must add another location (for example coord C) to get the real positions.
     */
    public static RegionAnalysis analyzeRegion(Level world, Location a, Location b, boolean checkTiles) {
        if (a == null || b == null || !a.isInWorld(world) || !Location.areCompatible(a, b)) return null;

        long pre = System.nanoTime();

        RegionAnalysis analysis = new RegionAnalysis();

        Vector3i deltas = MMUtils.getRegionDeltas(a, b);
        analysis.deltas = deltas;

        analysis.blocks = new ArrayList<>();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (Vector3i voxel : MMUtils.getBlocksInBB(a, deltas)) {
            pos.set(voxel.x, voxel.y, voxel.z);

            BlockSpec spec = BlockSpec.fromBlock(null, world, pos);

            if (spec.skipWhenCopying()) {
                continue;
            }

            PendingBlock pending = spec.instantiate(world, voxel.x, voxel.y, voxel.z);

            if (checkTiles) {
                pending.analyze(world.getBlockEntity(pos), PendingBlock.ANALYZE_ALL);
            }

            pending.x -= a.x;
            pending.y -= a.y;
            pending.z -= a.z;

            analysis.blocks.add(pending);
        }

        long post = System.nanoTime();

        if (DebugConfig.debug()) {
            ModernManipulator.LOG.info("Analysis took " + (post - pre) / 1e6 + " ms");
        }

        return analysis;
    }

    public static class RegionAnalysis {

        public Vector3i deltas;
        public List<PendingBlock> blocks;
    }

    /**
     * A fake apply context that tracks which items were consumed.
     */
    private static class BlockItemCheckContext implements IBlockApplyContext {

        public Level world;
        public int x, y, z;
        public Player player;

        public Object2LongOpenHashMap<ItemId> requiredItems = new Object2LongOpenHashMap<>();

        public Object2LongOpenHashMap<ItemId> storedItems = new Object2LongOpenHashMap<>();
        public Object2LongOpenHashMap<FluidId> storedFluids = new Object2LongOpenHashMap<>();

        @Override
        public Level getWorld() {
            return world;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public int getZ() {
            return z;
        }

        @Override
        public BlockEntity getTileEntity() {
            return world.getBlockEntity(new BlockPos(x, y, z));
        }

        @Override
        public Player getRealPlayer() {
            return player;
        }

        @Override
        public boolean tryApplyAction(double complexity) {
            return true;
        }

        @Override
        public BooleanObjectImmutablePair<List<BigItemStack>> tryConsumeItems(List<BigItemStack> items, int flags) {
            boolean simulate = (flags & CONSUME_SIMULATED) != 0;
            boolean fuzzy = (flags & CONSUME_FUZZY) != 0;

            if ((flags & CONSUME_REAL_ONLY) != 0) return BooleanObjectImmutablePair.of(false, new ArrayList<>());

            for (BigItemStack req : items) {
                if (req.getStackSize() == 0) {
                    continue;
                }

                if (!fuzzy) {
                    long amtInPending = storedItems.getLong(req.getId());

                    long toRemove = Math.min(amtInPending, req.getStackSize());

                    if (toRemove > 0) {
                        amtInPending -= toRemove;
                        req.decStackSize(toRemove);

                        if (!simulate) {
                            if (amtInPending == 0) {
                                storedItems.removeLong(req.getId());
                            } else {
                                storedItems.put(req.getId(), amtInPending);
                            }
                        }
                    }
                } else {
                    var iter = storedItems.object2LongEntrySet().iterator();

                    while (iter.hasNext()) {
                        var e = iter.next();

                        if (e.getLongValue() == 0) continue;

                        if (e.getKey().item() != req.getItem()) continue;

                        long amtInPending = e.getLongValue();
                        long toRemove = Math.min(amtInPending, req.getStackSize());

                        if (toRemove > 0) {
                            amtInPending -= toRemove;
                            req.decStackSize(toRemove);

                            if (!simulate) {
                                if (amtInPending == 0) {
                                    iter.remove();
                                } else {
                                    e.setValue(amtInPending);
                                }
                            }
                        }
                    }
                }

                if (req.getStackSize() > 0) requiredItems.addTo(req.getId(), req.getStackSize());
            }

            return BooleanObjectImmutablePair.of(true, items);
        }

        @Override
        public void givePlayerItems(List<BigItemStack> items) {
            for (BigItemStack item : items) {
                storedItems.addTo(item.getId(), item.stackSize);
            }
        }

        @Override
        public void givePlayerFluids(List<BigFluidStack> fluids) {
            for (BigFluidStack fluid : fluids) {
                storedFluids.addTo(fluid.getId(), fluid.amount);
            }
        }

        @Override
        public void warn(Component message) {
            sendWarningToPlayer(player, "mm.info.warning.only_message", x, y, z, message);
        }

        @Override
        public void error(Component message) {
            sendErrorToPlayer(player, "mm.info.error.only_message", x, y, z, message);
        }
    }

    public static class RequiredItemAnalysis {

        public Map<ItemId, Long> requiredItems;
        public Map<ItemId, Long> storedItems;
        public Map<FluidId, Long> storedFluids;
    }

    /**
     * Gets the required items for a build
     *
     * @param fromScratch When true, existing blocks will be ignored.
     */
    public static RequiredItemAnalysis getRequiredItemsForBuild(Player player, List<PendingBlock> blocks, boolean fromScratch) {
        BlockItemCheckContext context = new BlockItemCheckContext();
        context.player = player;
        context.world = player.level();

        BlockSpec pooled = new BlockSpec();

        for (PendingBlock block : blocks) {
            if (block.isInWorld(context.world)) {
                boolean isNew = true;

                BlockPos pos = new BlockPos(block.x, block.y, block.z);

                if (!fromScratch && !context.world.isEmptyBlock(pos)) {
                    BlockSpec.fromBlock(pooled, context.world, pos);

                    if (pooled.isEquivalent(block.spec)) {
                        isNew = false;
                    }
                }

                if (isNew && !block.isFree()) {
                    ItemStack stack = block.getStack();

                    if (!stack.isEmpty()) context.tryConsumeItems(stack);
                }

                context.x = block.x;
                context.y = block.y;
                context.z = block.z;

                if (isNew) {
                    block.getRequiredItemsForNewBlock(context);
                } else {
                    block.getRequiredItemsForExistingBlock(context);
                }
            }
        }

        RequiredItemAnalysis analysis = new RequiredItemAnalysis();
        analysis.requiredItems = context.requiredItems;
        analysis.storedItems = context.storedItems;
        analysis.storedFluids = context.storedFluids;

        return analysis;
    }
}
