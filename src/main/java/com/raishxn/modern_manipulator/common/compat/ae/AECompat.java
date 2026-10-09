package com.raishxn.modern_manipulator.common.compat.ae;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.parts.ICablePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.ImmutableBlockSpec;
import com.raishxn.modern_manipulator.common.building.InteropConstants;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.compat.CableHandlers;
import com.raishxn.modern_manipulator.common.compat.MEConnection;
import com.raishxn.modern_manipulator.common.compat.TileAnalyzers;
import com.raishxn.modern_manipulator.common.compat.ae.AEAnalysisResult.AEPartData;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.List;

/**
 * AE2 helpers. Only load this class when AE2 is present.
 */
public class AECompat {

    private AECompat() {}

    public static boolean isPartItem(Item item) {
        return item instanceof IPartItem<?>;
    }

    public static boolean isCableItem(Item item) {
        return item instanceof IPartItem<?> partItem && ICablePart.class.isAssignableFrom(partItem.getPartClass());
    }

    public static void init() {
        MEConnection.HOLDER.factory = AEConnection::connect;

        if (com.raishxn.modern_manipulator.common.utils.Mods.GregTech.isModLoaded()) {
            com.raishxn.modern_manipulator.common.compat.gt.GTSmartCopy.init();
        }

        TileAnalyzers.register((block, te, flags) -> {
            if ((flags & PendingBlock.ANALYZE_AE) != 0) {
                AEAnalysisResult ae = AEAnalysisResult.analyze(te, null);
                if (ae != null) block.ae = ae;
            }
        });

        IGridLinkableHandler linkHandler = new IGridLinkableHandler() {

            @Override
            public boolean canLink(ItemStack stack) {
                return stack.getItem() instanceof ItemMatterManipulator &&
                        ItemMatterManipulator.getState(stack).hasCap(ItemMatterManipulator.CONNECTS_TO_AE);
            }

            @Override
            public void link(ItemStack stack, GlobalPos pos) {
                ((ItemMatterManipulator) stack.getItem()).setMELink(
                        stack,
                        new Location(pos.dimension().location().toString(), pos.pos().getX(), pos.pos().getY(),
                                pos.pos().getZ()));
            }

            @Override
            public void unlink(ItemStack stack) {
                ((ItemMatterManipulator) stack.getItem()).setMELink(stack, null);
            }
        };

        GridLinkables.register(MMItems.MK1.get(), linkHandler);
        GridLinkables.register(MMItems.MK2.get(), linkHandler);
        GridLinkables.register(MMItems.MK3.get(), linkHandler);

        CableHandlers.register(new CableHandlers.ICableHandler() {

            @Override
            public boolean pickCable(BlockSpec spec, Level world, BlockPos pos) {
                return getCableInWorld(spec, world, pos);
            }

            @Override
            public boolean getCableInWorld(BlockSpec spec, Level world, BlockPos pos) {
                if (!(world.getBlockEntity(pos) instanceof IPartHost host)) return false;

                IPart cable = host.getPart(null);

                if (cable == null) return false;

                spec.setObject(new ItemStack(cable.getPartItem()));

                return true;
            }

            private AEAnalysisResult existing(Level world, BlockPos pos) {
                BlockEntity te = world.getBlockEntity(pos);

                AEAnalysisResult ae = te instanceof IPartHost ? AEAnalysisResult.analyze(te, null) : null;

                if (ae == null) ae = new AEAnalysisResult();
                if (ae.mAEParts == null) ae.mAEParts = new AEPartData[7];

                return ae;
            }

            private PendingBlock cableBus(Level world, BlockPos pos, AEAnalysisResult ae) {
                PendingBlock block = new BlockSpec().setObject(InteropConstants.getAECableBus().defaultBlockState())
                        .instantiate(world, pos.getX(), pos.getY(), pos.getZ());

                block.ae = ae;

                return block;
            }

            @Override
            public @Nullable PendingBlock instantiateExchange(MMState state, ImmutableBlockSpec replacement,
                                                              Level world, BlockPos pos) {
                if (!state.hasCap(ItemMatterManipulator.ALLOW_CABLES) || !isCableItem(replacement.getItem()))
                    return null;

                AEAnalysisResult ae = existing(world, pos);
                ae.mAEParts[AEAnalysisResult.CENTER] = new AEPartData(replacement.getItem());

                return cableBus(world, pos, ae);
            }

            @Override
            public @Nullable PendingBlock getCableRemoval(Level world, BlockPos pos) {
                if (!(world.getBlockEntity(pos) instanceof IPartHost host) || host.getPart(null) == null) return null;

                AEAnalysisResult ae = existing(world, pos);
                ae.mAEParts[AEAnalysisResult.CENTER] = null;

                return cableBus(world, pos, ae);
            }

            @Override
            public boolean getCables(Vector3i a, Vector3i b, List<Vector3i> voxels, List<PendingBlock> out, Level world,
                                     ImmutableBlockSpec cable) {
                if (!isCableItem(cable.getItem())) return false;

                for (Vector3i voxel : voxels) {
                    BlockPos pos = new BlockPos(voxel.x, voxel.y, voxel.z);

                    AEAnalysisResult ae = existing(world, pos);
                    ae.mAEParts[AEAnalysisResult.CENTER] = new AEPartData(cable.getItem());

                    out.add(cableBus(world, pos, ae));
                }

                return true;
            }
        });
    }
}
