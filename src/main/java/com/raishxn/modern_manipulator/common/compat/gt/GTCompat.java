package com.raishxn.modern_manipulator.common.compat.gt;

import com.gregtechceu.gtceu.api.block.OreBlock;
import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.electric.BatteryBufferMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.ImmutableBlockSpec;
import com.raishxn.modern_manipulator.common.building.InventoryAdapter;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.compat.BlockRemovers;
import com.raishxn.modern_manipulator.common.compat.CableHandlers;
import com.raishxn.modern_manipulator.common.compat.TileAnalyzers;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.List;

/**
 * Registers every GregTech integration.
 */
public class GTCompat {

    private GTCompat() {}

    /**
     * Sends the full state of the machines/pipes to the clients again and makes them rebuild their models.
     * GTCEu doesn't override getUpdatePacket, so sendBlockUpdated carries no block entity data: the client creates an
     * empty block entity and relies on ldlib's async payload, which gets dropped when it arrives before the block
     * entity
     * exists client side. The vanilla data packet carries ldlib's full sync tag (see ldlib's BlockEntityMixin).
     */
    public static void resync(net.minecraft.server.level.ServerLevel level, List<BlockPos> positions) {
        it.unimi.dsi.fastutil.longs.LongArrayList synced = new it.unimi.dsi.fastutil.longs.LongArrayList();

        for (BlockPos pos : positions) {
            BlockEntity te = level.getBlockEntity(pos);

            if (te instanceof IMachineBlockEntity mbe) {
                MetaMachine machine = mbe.getMetaMachine();
                machine.getSyncStorage().markAllDirty();
                machine.getCoverContainer().getCovers().forEach(cover -> cover.getSyncStorage().markAllDirty());
            } else if (te instanceof PipeBlockEntity<?, ?> pipe) {
                pipe.getSyncStorage().markAllDirty();
                pipe.getCoverContainer().getCovers().forEach(cover -> cover.getSyncStorage().markAllDirty());
            } else {
                continue;
            }

            var packet = net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(te);
            for (var player : level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(pos),
                    false)) {
                player.connection.send(packet);
            }

            synced.add(pos.asLong());
        }

        if (synced.isEmpty()) return;

        // sent after the data packets so the models are rebuilt with the new connections/covers
        com.raishxn.modern_manipulator.common.networking.Messages.RerenderBlocks.sendToPlayersAround(
                new com.raishxn.modern_manipulator.common.items.manipulator.Location(level, positions.get(0)), synced);
    }

    private record PendingResync(net.minecraft.server.level.ServerLevel level, List<BlockPos> positions, int tick) {}

    private static final List<PendingResync> PENDING_RESYNCS = new java.util.ArrayList<>();

    /** Re-sends the machines/pipes to the clients at the end of the next tick (see {@link #resync}). */
    public static void scheduleResync(net.minecraft.server.level.ServerLevel level, List<BlockPos> positions) {
        PENDING_RESYNCS.add(new PendingResync(level, positions, level.getServer().getTickCount()));
    }

    private static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || PENDING_RESYNCS.isEmpty()) return;

        int now = event.getServer().getTickCount();

        PENDING_RESYNCS.removeIf(pending -> {
            if (pending.tick() >= now) return false;

            resync(pending.level(), pending.positions());
            return true;
        });
    }

    public static void init() {
        TileAnalyzers.register((block, te, flags) -> {
            if ((flags & PendingBlock.ANALYZE_GT) != 0) {
                GTAnalysisResult gt = GTAnalysisResult.analyze(te);
                if (gt != null) block.gt = gt;
            }
        });

        InventoryAdapter.register(new InventoryAdapter() {

            @Override
            public boolean canHandle(BlockEntity te) {
                return te instanceof IMachineBlockEntity || te instanceof PipeBlockEntity<?, ?>;
            }

            @Override
            public @Nullable IItemHandler getHandler(BlockEntity te) {
                if (!(te instanceof IMachineBlockEntity mbe)) return null;

                MetaMachine machine = mbe.getMetaMachine();

                // only machines whose inventory is part of their configuration are copied (busses aren't)
                if (machine instanceof BatteryBufferMachine buffer) return buffer.getBatteryInventory();
                // the turbine rotor (also covers addon rotor holders extending it)
                if (machine instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine rotorHolder) {
                    return rotorHolder.inventory.storage;
                }

                return null;
            }
        });

        // configuring a machine/pipe in the same tick it's placed doesn't reach the client: ldlib's sync payload can
        // arrive before the block itself (block changes are only broadcast during the next tick's chunk tick) and gets
        // dropped. Everything is re-sent at the end of the next tick, after that broadcast.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(GTCompat::onServerTick);
        com.raishxn.modern_manipulator.common.compat.BuildHooks
                .register(new com.raishxn.modern_manipulator.common.compat.BuildHooks.IBuildHook() {

                    @Override
                    public void onBlocksPlaced(com.raishxn.modern_manipulator.common.building.PendingBuild build,
                                               Level world, List<PendingBlock> placed,
                                               com.raishxn.modern_manipulator.common.building.IBlockApplyContext context) {
                        if (!(world instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;

                        List<BlockPos> positions = new java.util.ArrayList<>();

                        for (PendingBlock block : placed) {
                            if (block.gt != null) positions.add(new BlockPos(block.x, block.y, block.z));
                        }

                        if (positions.isEmpty()) return;

                        scheduleResync(serverLevel, positions);
                    }
                });

        PendingBlock.ITEM_PREVIEW_BLOCKS.add(state -> state.getBlock() instanceof PipeBlock<?, ?, ?>);

        BlockRemovers.registerOreChecker(state -> state.getBlock() instanceof OreBlock);

        BlockRemovers.registerTankBlacklist(te -> te instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine().getClass().getSimpleName().contains("Stocking"));

        CableHandlers.register(new CableHandlers.ICableHandler() {

            @Override
            public boolean pickCable(BlockSpec spec, Level world, BlockPos pos) {
                if (!(world.getBlockState(pos).getBlock() instanceof PipeBlock<?, ?, ?> pipe)) return false;

                spec.setObject(pipe.defaultBlockState());

                return true;
            }

            @Override
            public boolean getCables(Vector3i a, Vector3i b, List<Vector3i> voxels, List<PendingBlock> out, Level world,
                                     ImmutableBlockSpec cable) {
                if (!(cable.getBlock() instanceof PipeBlock<?, ?, ?>)) return false;

                int start = 0, end = 0;

                // calculate the start & end connection flags
                switch (new Vector3i(b).sub(a).absolute().maxComponent()) {
                    case 0 -> {
                        start = 1 << (b.x < a.x ? Direction.EAST : Direction.WEST).ordinal();
                        end = 1 << (b.x > a.x ? Direction.EAST : Direction.WEST).ordinal();
                    }
                    case 1 -> {
                        start = 1 << (b.y < a.y ? Direction.UP : Direction.DOWN).ordinal();
                        end = 1 << (b.y > a.y ? Direction.UP : Direction.DOWN).ordinal();
                    }
                    case 2 -> {
                        start = 1 << (b.z < a.z ? Direction.SOUTH : Direction.NORTH).ordinal();
                        end = 1 << (b.z > a.z ? Direction.SOUTH : Direction.NORTH).ordinal();
                    }
                }

                for (int i = 0; i < voxels.size(); i++) {
                    Vector3i voxel = voxels.get(i);
                    BlockPos pos = new BlockPos(voxel.x, voxel.y, voxel.z);

                    GTAnalysisResult gt = null;

                    if (world.getBlockState(pos).getBlock() == cable.getBlock()) {
                        gt = GTAnalysisResult.analyze(world.getBlockEntity(pos));
                    }

                    if (gt == null) gt = new GTAnalysisResult();
                    if (gt.mConnections == -1) gt.mConnections = 0;

                    if (i > 0) gt.mConnections |= start;
                    if (i < voxels.size() - 1) gt.mConnections |= end;

                    PendingBlock pendingBlock = cable.instantiate(world, voxel.x, voxel.y, voxel.z);

                    pendingBlock.gt = gt;

                    out.add(pendingBlock);
                }

                return true;
            }
        });
    }
}
