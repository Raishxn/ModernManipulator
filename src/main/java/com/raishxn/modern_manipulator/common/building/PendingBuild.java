package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.compat.BuildHooks;
import com.raishxn.modern_manipulator.common.compat.PlacementHandlers;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.networking.Messages;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendInfoToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendWarningToPlayer;

/**
 * Handles all building logic.
 */
public class PendingBuild extends AbstractBuildable {

    private final Deque<PendingBlock> pendingBlocks;
    private final LongOpenHashSet visited = new LongOpenHashSet();

    private final LongList errors = new LongArrayList();
    private final LongList warnings = new LongArrayList();

    public PendingBuild(Player player, MMState state, ManipulatorTier tier, List<PendingBlock> pendingBlocks) {
        super(player, state, tier);
        this.pendingBlocks = new ArrayDeque<>(pendingBlocks);
    }

    @Override
    public void tryPlaceBlocks(ItemStack stack, Player player) {
        resetWarnings();
        refillPower(stack);

        int placeSpeed = tier.getPlaceSpeed();

        List<PendingBlock> toPlace = new ArrayList<>(Math.min(placeSpeed, 1024));

        int lastChunkX = Integer.MIN_VALUE, lastChunkZ = Integer.MIN_VALUE;
        int shuffleCount = 0;

        Level world = player.level();

        BuildHooks.onBuildTick(this, world);

        PendingBuildApplyContext applyContext = new PendingBuildApplyContext(stack);

        BlockSpec pooled = new BlockSpec();

        // check every pending block that's left
        while (toPlace.size() < placeSpeed && !pendingBlocks.isEmpty()) {
            PendingBlock next = pendingBlocks.getFirst();

            int x = next.x, y = next.y, z = next.z;
            BlockPos pos = new BlockPos(x, y, z);

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            // if this block's chunk isn't loaded, ignore it completely
            if (chunkX != lastChunkX || chunkZ != lastChunkZ) {
                if (!world.hasChunk(chunkX, chunkZ)) {
                    pendingBlocks.removeFirst();
                    continue;
                } else {
                    lastChunkX = chunkX;
                    lastChunkZ = chunkZ;
                }
            }

            if (world.isOutsideBuildHeight(y)) {
                pendingBlocks.removeFirst();
                continue;
            }

            // if this block is protected, ignore it completely and print a warning
            if (!isEditable(world, x, y, z, true)) {
                pendingBlocks.removeFirst();
                continue;
            }

            // if this block is different from the last one, stop checking blocks
            // since the pending blocks are sorted by their contained block, this is usually true
            if (!toPlace.isEmpty() && !next.spec.isEquivalent(toPlace.get(0).spec)) {
                break;
            }

            PendingBlock existing = PendingBlock.fromBlock(world, x, y, z);
            BlockState existingState = world.getBlockState(pos);

            if (next.spec.isAir() && existingState.isAir()) {
                pendingBlocks.removeFirst();
                continue;
            }

            // if the existing block is the same as the one we're trying to place, just apply its tile data
            if (PendingBlock.areEquivalent(existing, next) && !BuildHooks.requiresReplacement(world, pos, next)) {
                PendingBlock block = pendingBlocks.removeFirst();

                if (supportsConfiguring()) {
                    applyContext.pendingBlock = block;
                    block.apply(applyContext, world);
                    playSound(world, x, y, z, SoundEvents.ENDERMAN_TELEPORT);
                }

                BuildHooks.onBlockApplied(this, world, pos, block);

                continue;
            }

            // checks if the existing block is removable
            boolean canPlace = switch (state.config.removeMode) {
                case NONE -> existingState.isAir();
                case REPLACEABLE -> existingState.canBeReplaced();
                case ALL -> true;
            };

            canPlace &= existingState.getDestroySpeed(world, pos) >= 0;

            // we don't want to remove these even though they'll never be placed because we want to see how many blocks
            // couldn't be placed
            if (!canPlace) {
                pendingBlocks.addLast(pendingBlocks.removeFirst());
                shuffleCount++;

                if (shuffleCount > pendingBlocks.size()) {
                    break;
                } else {
                    continue;
                }
            }

            // if there's an existing block then remove it if possible
            if (!existingState.isAir()) {
                if (!state.hasCap(ItemMatterManipulator.ALLOW_REMOVING)) {
                    pendingBlocks.removeFirst();
                    continue;
                }

                if (!tryConsumePower(stack, world, x, y, z, existing.spec)) {
                    sendErrorToPlayer(player, "mm.info.error.out_of_eu");
                    break;
                }
            }

            // check block dependencies for things like levers
            // if we can't place this block, shuffle it to the back of the list
            if (!next.spec.isAir() && next.spec.isBlockSpec() && !next.getBlockState().canSurvive(world, pos)) {
                pendingBlocks.addLast(pendingBlocks.removeFirst());
                shuffleCount++;

                // if we've shuffled every block, then we'll never be able to place any of them
                if (shuffleCount > pendingBlocks.size()) {
                    break;
                } else {
                    continue;
                }
            }

            if (!tryConsumePower(stack, world, x, y, z, next.spec)) {
                sendErrorToPlayer(player, "mm.info.error.out_of_eu");
                break;
            }

            long coord = pos.asLong();

            if (!visited.add(coord)) {
                ModernManipulator.LOG.warn("Tried to place block twice! " + next);
                pendingBlocks.removeFirst();
                continue;
            }

            toPlace.add(pendingBlocks.removeFirst());
        }

        // check if we could place any blocks
        if (toPlace.isEmpty()) {
            if (!pendingBlocks.isEmpty()) {
                sendErrorToPlayer(player, "mm.info.error.could_not_place", pendingBlocks.size());
            } else {
                sendInfoToPlayer(player, "mm.info.finished_placing");
            }

            actuallyGivePlayerStuff();
            playSounds();
            return;
        }

        PendingBlock first = toPlace.get(0);

        ItemStack perBlock = first.getStack();
        long total = 0;
        BigItemStack extracted = null;

        // if the block we're placing isn't free (ae cable busses) we need to consume it
        if (!first.isFree() && !perBlock.isEmpty()) {
            total = toPlace.size() * (long) perBlock.getCount();
            extracted = MMItemConsumer.consume(applyContext, BigItemStack.create(perBlock).setStackSize(total));

            if (extracted == null) {
                sendWarningToPlayer(player, "mm.info.warning.could_not_find", toPlace.size());
                sendWarningToPlayer(player, "mm.info.warning.of_item", first.getDisplayNameChat(), total);

                for (PendingBlock pending : toPlace) {
                    pendingBlocks.add(pending);

                    visited.remove(BlockPos.asLong(pending.x, pending.y, pending.z));
                }

                toPlace.clear();
            }
        }

        int i = 0;
        for (; i < toPlace.size(); i++) {
            PendingBlock pending = toPlace.get(i);

            int x = pending.x;
            int y = pending.y;
            int z = pending.z;
            BlockPos pos = new BlockPos(x, y, z);

            playSound(world, x, y, z, SoundEvents.ENDERMAN_TELEPORT);

            BlockSpec existing = BlockSpec.fromBlock(pooled, world, pos);

            if (existing.equals(pending.spec)) {
                // somehow the block already exists, despite us checking to make sure that this shouldn't happen
                // just to be safe, we only consume the item when we actually place something
                if (supportsConfiguring()) {
                    applyContext.pendingBlock = pending;
                    pending.apply(applyContext, world);
                }

                BuildHooks.onBlockApplied(this, world, pos, pending);

                continue;
            }

            if (extracted != null && extracted.stackSize < perBlock.getCount()) {
                break;
            }

            if (!world.getBlockState(pos).isAir()) {
                removeBlock(world, x, y, z, existing);
            }

            applyContext.pendingBlock = pending;

            if (!pending.spec.isAir()) {
                if (!PlacementHandlers.place(world, pos, pending, perBlock.copy(), player, applyContext)) {
                    if (!placeBlock(world, pos, pending, perBlock.copy())) {
                        continue;
                    }
                }
            }

            if (extracted != null) {
                extracted.stackSize -= perBlock.getCount();
            }

            pending.apply(applyContext, world);

            BuildHooks.onBlockApplied(this, world, pos, pending);
        }

        if (extracted != null && i < toPlace.size()) {
            sendWarningToPlayer(player, "mm.info.warning.could_not_find", toPlace.size() - i);
            sendWarningToPlayer(
                    player,
                    "mm.info.warning.of_item",
                    first.getDisplayNameChat(),
                    total - (long) (toPlace.size() - i) * perBlock.getCount());
        }

        sendInfoToPlayer(player, "mm.info.placed_remaining", i, pendingBlocks.size());

        if (extracted != null && extracted.stackSize >= perBlock.getCount() && !perBlock.isEmpty()) {
            // extra stuff left over somehow
            ModernManipulator.LOG.error(
                    "Didn't consume enough items! " + perBlock.getHoverName().getString() + "; expected to consume " +
                            total +
                            ", but consumed " + (total - extracted.stackSize));
            givePlayerItems(extracted.toStacks().toArray(new ItemStack[0]));
        }

        for (; i < toPlace.size(); i++) {
            PendingBlock pending = toPlace.get(i);

            pendingBlocks.add(pending);

            visited.remove(BlockPos.asLong(pending.x, pending.y, pending.z));
        }

        BuildHooks.onBlocksPlaced(this, world, toPlace, applyContext);

        sendRerender(world, toPlace);

        actuallyGivePlayerStuff();
        playSounds();
    }

    /**
     * Tells nearby clients to refresh the blocks that were configured (covers, pipe connections).
     */
    private void sendRerender(Level world, List<PendingBlock> placed) {
        if (placed.isEmpty() || !(world instanceof net.minecraft.server.level.ServerLevel)) return;

        LongArrayList positions = new LongArrayList();

        for (PendingBlock block : placed) {
            if (block.gt != null || block.ae != null) positions.add(BlockPos.asLong(block.x, block.y, block.z));
        }

        if (positions.isEmpty()) return;

        PendingBlock first = placed.get(0);

        Messages.RerenderBlocks.sendToPlayersAround(first, positions);
    }

    /**
     * Places a normal block.
     */
    public boolean placeBlock(Level world, BlockPos pos, PendingBlock pending, ItemStack stack) {
        BlockState target = pending.getBlockState();

        BlockState placed = Block.updateFromNeighbourShapes(target, world, pos);

        if (!world.setBlock(pos, placed, Block.UPDATE_ALL)) {
            return false;
        }

        BlockState inWorld = world.getBlockState(pos);

        if (inWorld.is(target.getBlock())) {
            target.getBlock().setPlacedBy(world, pos, inWorld, player, stack);
        }

        return true;
    }

    @Override
    public void onStopped() {
        if (!pendingItems.isEmpty() || !pendingFluids.isEmpty()) {
            ModernManipulator.LOG.error("Build stopped without delivering all items! There's a bug somewhere!");
        }

        actuallyGivePlayerStuff();

        if (player instanceof ServerPlayer serverPlayer) {
            Messages.BuildStatus.sendToPlayer(serverPlayer, Pair.of(errors, warnings));
        }
    }

    private boolean supportsConfiguring() {
        // self-explanatory
        if (state.hasCap(ItemMatterManipulator.ALLOW_CONFIGURING)) return true;

        // lower tiers support cables, but not copying
        // since exchanging or placing cables requires configuring, we need to return true for these two
        if (state.config.placeMode == PlaceMode.EXCHANGING) return true;
        return state.config.placeMode == PlaceMode.CABLES;
    }

    public class PendingBuildApplyContext implements IBlockApplyContext {

        public static final double EU_PER_ACTION = 8192;

        public ItemStack manipulatorItemStack;
        public PendingBlock pendingBlock;

        public PendingBuildApplyContext(ItemStack manipulatorItemStack) {
            this.manipulatorItemStack = manipulatorItemStack;
        }

        @Override
        public Level getWorld() {
            return player.level();
        }

        @Override
        public int getX() {
            return pendingBlock.x;
        }

        @Override
        public int getY() {
            return pendingBlock.y;
        }

        @Override
        public int getZ() {
            return pendingBlock.z;
        }

        @Override
        public BlockEntity getTileEntity() {
            if (pendingBlock.isInWorld(player.level())) {
                return player.level().getBlockEntity(new BlockPos(pendingBlock.x, pendingBlock.y, pendingBlock.z));
            } else {
                return null;
            }
        }

        @Override
        public Player getRealPlayer() {
            return player;
        }

        @Override
        public boolean tryApplyAction(double complexity) {
            return PendingBuild.this
                    .tryConsumePower(manipulatorItemStack, pendingBlock.x, pendingBlock.y, pendingBlock.z,
                            EU_PER_ACTION * complexity);
        }

        @Override
        public BooleanObjectImmutablePair<List<BigItemStack>> tryConsumeItems(List<BigItemStack> items, int flags) {
            return PendingBuild.this.tryConsumeItems(items, flags);
        }

        @Override
        public void givePlayerItems(List<BigItemStack> items) {
            PendingBuild.this.givePlayerItems(items);
        }

        @Override
        public void givePlayerFluids(List<BigFluidStack> fluids) {
            PendingBuild.this.givePlayerFluids(fluids);
        }

        private Component getBlockName() {
            if (!pendingBlock.isInWorld(player.level())) return null;

            return BlockSpec.fromBlock(null, player.level(), pendingBlock.x, pendingBlock.y, pendingBlock.z)
                    .getChatComponent();
        }

        @Override
        public void warn(Component message) {
            Component blockName = getBlockName();

            if (blockName != null) {
                sendWarningToPlayer(player, "mm.info.warning.with_block", pendingBlock.x, pendingBlock.y,
                        pendingBlock.z, blockName, message);
            } else {
                sendWarningToPlayer(player, "mm.info.warning.only_message", pendingBlock.x, pendingBlock.y,
                        pendingBlock.z, message);
            }

            PendingBuild.this.warnings.add(BlockPos.asLong(pendingBlock.x, pendingBlock.y, pendingBlock.z));
        }

        @Override
        public void error(Component message) {
            Component blockName = getBlockName();

            if (blockName != null) {
                sendErrorToPlayer(player, "mm.info.error.with_block", pendingBlock.x, pendingBlock.y, pendingBlock.z,
                        blockName, message);
            } else {
                sendErrorToPlayer(player, "mm.info.error.only_message", pendingBlock.x, pendingBlock.y, pendingBlock.z,
                        message);
            }

            PendingBuild.this.errors.add(BlockPos.asLong(pendingBlock.x, pendingBlock.y, pendingBlock.z));
        }
    }
}
