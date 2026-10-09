package com.raishxn.modern_manipulator.common.compat.gt;

import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.ImmutableBlockSpec;
import com.raishxn.modern_manipulator.common.building.InventoryAdapter;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.compat.BlockRemovers;
import com.raishxn.modern_manipulator.common.compat.CableHandlers;
import com.raishxn.modern_manipulator.common.compat.TileAnalyzers;

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

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.List;

/**
 * Registers every GregTech integration.
 */
public class GTCompat {

    private GTCompat() {}

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

                return null;
            }
        });

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
