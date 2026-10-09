package com.raishxn.modern_manipulator.common.compat.gt;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.IBlockApplyContext;
import com.raishxn.modern_manipulator.common.building.ITileAnalysisIntegration;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDistinctPart;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The GregTech settings of a machine or pipe.
 */
public class GTAnalysisResult implements ITileAnalysisIntegration {

    /** Pipe connections (bit per direction ordinal), -1 when not a pipe */
    public int mConnections = -1;
    public int mBlockedConnections = 0;
    public int mGTColour = -1;
    public Boolean mWorkingEnabled = null;
    public Direction mItemOutput = null, mFluidOutput = null;
    public boolean mAutoOutputItems, mAutoOutputFluids, mAllowInputFromOutputItems, mAllowInputFromOutputFluids;
    public Boolean mMuffled = null;
    public Boolean mDistinct = null;
    /** Multiblock controller settings: voiding mode name, batch mode, selected recipe type */
    public String mVoidingMode = null;
    public Boolean mBatchEnabled = null;
    public int mActiveRecipeType = -1;
    /** -1 = no circuit slot, 0 = empty */
    public int mGTGhostCircuit = -1;
    public CoverData[] mCovers = null;
    /** Machine specific config (ICopyable#copyConfig) */
    public CompoundTag mGTData = null;

    public static class CoverData {

        public String id;
        public CompoundTag item;
        public CompoundTag data;

        public ItemStack getCoverStack() {
            return ItemStack.of(item);
        }

        @Override
        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public CoverData clone() {
            CoverData dup = new CoverData();
            dup.id = id;
            dup.item = item == null ? null : item.copy();
            dup.data = data == null ? null : data.copy();
            return dup;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CoverData other && Objects.equals(id, other.id) && Objects.equals(item, other.item) &&
                Objects.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, item, data);
        }

        public static CoverData fromCover(CoverBehavior cover) {
            CoverData data = new CoverData();
            data.id = GTRegistries.COVERS.getKey(cover.coverDefinition).toString();
            data.item = cover.getAttachItem().copyWithCount(1).save(new CompoundTag());
            data.data = cover.copyConfig(new CompoundTag());
            return data;
        }
    }

    private static final GTAnalysisResult NO_OP = new GTAnalysisResult();

    public static @Nullable GTAnalysisResult analyze(BlockEntity te) {
        GTAnalysisResult result;

        if (te instanceof IMachineBlockEntity mbe) {
            result = new GTAnalysisResult(mbe.getMetaMachine());
        } else if (te instanceof PipeBlockEntity<?, ?> pipe) {
            result = new GTAnalysisResult(pipe);
        } else {
            return null;
        }

        return result.equals(NO_OP) ? null : result;
    }

    public GTAnalysisResult() {}

    private void analyzeCovers(ICoverable coverable) {
        CoverData[] covers = new CoverData[6];
        boolean hasCover = false;

        for (Direction dir : Direction.values()) {
            CoverBehavior cover = coverable.getCoverAtSide(dir);

            if (cover != null) {
                covers[dir.ordinal()] = CoverData.fromCover(cover);
                hasCover = true;
            }
        }

        if (hasCover) mCovers = covers;
    }

    public GTAnalysisResult(PipeBlockEntity<?, ?> pipe) {
        mConnections = pipe.getConnections();
        mBlockedConnections = pipe.getBlockedConnections();
        mGTColour = pipe.getPaintingColor();

        analyzeCovers(pipe.getCoverContainer());
    }

    public GTAnalysisResult(MetaMachine machine) {
        mGTColour = machine.getPaintingColor();

        if (machine instanceof IControllable controllable) {
            mWorkingEnabled = controllable.isWorkingEnabled();
        }

        if (machine instanceof IAutoOutputItem autoOutputItem && autoOutputItem.getOutputFacingItems() != null) {
            mItemOutput = autoOutputItem.getOutputFacingItems();
            mAutoOutputItems = autoOutputItem.isAutoOutputItems();
            mAllowInputFromOutputItems = autoOutputItem.isAllowInputFromOutputSideItems();
        }

        if (machine instanceof IAutoOutputFluid autoOutputFluid && autoOutputFluid.getOutputFacingFluids() != null) {
            mFluidOutput = autoOutputFluid.getOutputFacingFluids();
            mAutoOutputFluids = autoOutputFluid.isAutoOutputFluids();
            mAllowInputFromOutputFluids = autoOutputFluid.isAllowInputFromOutputSideFluids();
        }

        if (machine instanceof IMufflableMachine mufflable) {
            mMuffled = mufflable.isMuffled();
        }

        if (machine instanceof IDistinctPart distinct) {
            mDistinct = distinct.isDistinct();
        }

        if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.IVoidable voidable) {
            mVoidingMode = voidable.getVoidingMode().name();
        }

        if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine electric) {
            mBatchEnabled = electric.isBatchEnabled();
        }

        if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine recipeMachine) {
            mActiveRecipeType = recipeMachine.getActiveRecipeType();
        }

        if (machine instanceof IHasCircuitSlot circuitMachine && circuitMachine.isCircuitSlotEnabled()) {
            mGTGhostCircuit = IntCircuitBehaviour.getCircuitConfiguration(circuitMachine.getCircuitInventory().getStackInSlot(0));
        }

        analyzeCovers(machine.getCoverContainer());

        try {
            CompoundTag data = machine.copyConfig(new CompoundTag());

            if (data != null && !data.isEmpty()) mGTData = data;
        } catch (Throwable t) {
            ModernManipulator.LOG.info("Could not copy machine config. This is not a crash, but it could cause issues.", t);
        }
    }

    @Override
    public boolean apply(IBlockApplyContext ctx) {
        BlockEntity te = ctx.getTileEntity();

        ServerPlayer player = ctx.getRealPlayer() instanceof ServerPlayer sp ? sp : null;

        if (te instanceof PipeBlockEntity<?, ?> pipe) {
            if (mGTColour != pipe.getPaintingColor()) pipe.setPaintingColor(mGTColour);

            if (mConnections != -1) {
                for (Direction dir : Direction.values()) {
                    boolean shouldBeConnected = (mConnections & (1 << dir.ordinal())) != 0;

                    if (PipeBlockEntity.isConnected(pipe.getConnections(), dir) != shouldBeConnected) {
                        pipe.setConnection(dir, shouldBeConnected, false);
                    }

                    boolean shouldBeBlocked = (mBlockedConnections & (1 << dir.ordinal())) != 0;

                    if (PipeBlockEntity.isFaceBlocked(pipe.getBlockedConnections(), dir) != shouldBeBlocked) {
                        pipe.setBlocked(dir, shouldBeBlocked);
                    }
                }
            }

            applyCovers(ctx, pipe.getCoverContainer(), player);

            return true;
        }

        if (!(te instanceof IMachineBlockEntity mbe)) return true;

        MetaMachine machine = mbe.getMetaMachine();

        if (machine.getPaintingColor() != mGTColour) machine.setPaintingColor(mGTColour);

        if (machine instanceof IControllable controllable && mWorkingEnabled != null) {
            controllable.setWorkingEnabled(mWorkingEnabled);
        }

        if (machine instanceof IAutoOutputItem autoOutputItem && mItemOutput != null) {
            autoOutputItem.setOutputFacingItems(mItemOutput);
            autoOutputItem.setAutoOutputItems(mAutoOutputItems);
            autoOutputItem.setAllowInputFromOutputSideItems(mAllowInputFromOutputItems);
        }

        if (machine instanceof IAutoOutputFluid autoOutputFluid && mFluidOutput != null) {
            autoOutputFluid.setOutputFacingFluids(mFluidOutput);
            autoOutputFluid.setAutoOutputFluids(mAutoOutputFluids);
            autoOutputFluid.setAllowInputFromOutputSideFluids(mAllowInputFromOutputFluids);
        }

        if (machine instanceof IMufflableMachine mufflable && mMuffled != null) {
            mufflable.setMuffled(mMuffled);
        }

        if (machine instanceof IDistinctPart distinct && mDistinct != null) {
            distinct.setDistinct(mDistinct);
        }

        if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.IVoidable voidable && mVoidingMode != null) {
            try {
                var mode = com.gregtechceu.gtceu.api.machine.feature.IVoidable.VoidingMode.valueOf(mVoidingMode);
                if (voidable.getVoidingMode() != mode) voidable.setVoidingMode(mode);
            } catch (IllegalArgumentException ignored) {}
        }

        if (machine instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine electric &&
            mBatchEnabled != null && electric.isBatchEnabled() != mBatchEnabled) {
            electric.setBatchEnabled(mBatchEnabled);
        }

        if (machine instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine recipeMachine && mActiveRecipeType >= 0 &&
            recipeMachine.getActiveRecipeType() != mActiveRecipeType && mActiveRecipeType < recipeMachine.getRecipeTypes().length) {
            recipeMachine.setActiveRecipeType(mActiveRecipeType);
        }

        if (machine instanceof IHasCircuitSlot circuitMachine && mGTGhostCircuit != -1 && circuitMachine.isCircuitSlotEnabled()) {
            ItemStack current = circuitMachine.getCircuitInventory().getStackInSlot(0);

            if (IntCircuitBehaviour.getCircuitConfiguration(current) != mGTGhostCircuit || (mGTGhostCircuit == 0) != current.isEmpty()) {
                circuitMachine.getCircuitInventory()
                    .setStackInSlot(0, mGTGhostCircuit == 0 ? ItemStack.EMPTY : IntCircuitBehaviour.stack(mGTGhostCircuit));
            }
        }

        applyCovers(ctx, machine.getCoverContainer(), player);

        if (mGTData != null && player != null) {
            try {
                machine.pasteConfig(player, mGTData.copy());
            } catch (Throwable t) {
                ctx.warn(Component.translatable("mm.info.warning.could_not_paste_config", t.getMessage()));
            }
        }

        return true;
    }

    private void applyCovers(IBlockApplyContext ctx, ICoverable coverable, @Nullable ServerPlayer player) {
        // install/remove/update the covers
        for (Direction dir : Direction.values()) {
            CoverData expected = mCovers == null ? null : mCovers[dir.ordinal()];
            CoverBehavior actual = coverable.getCoverAtSide(dir);

            if (actual == null && expected != null) {
                installCover(ctx, coverable, dir, expected, player);
            } else if (actual != null && expected == null) {
                removeCover(ctx, coverable, dir);
            } else if (actual != null) {
                CoverData actualData = CoverData.fromCover(actual);

                if (!Objects.equals(actualData.id, expected.id) ||
                    !ItemStack.isSameItemSameTags(actual.getAttachItem(), expected.getCoverStack())) {
                    removeCover(ctx, coverable, dir);
                    installCover(ctx, coverable, dir, expected, player);
                } else if (!Objects.equals(actualData.data, expected.data)) {
                    if (player != null && expected.data != null) actual.pasteConfig(player, expected.data.copy());
                }
            }
        }
    }

    private void installCover(IBlockApplyContext ctx, ICoverable coverable, Direction side, CoverData cover,
                              @Nullable ServerPlayer player) {
        CoverDefinition def = GTRegistries.COVERS.get(new ResourceLocation(cover.id));

        if (def == null) {
            ctx.warn(Component.translatable("mm.info.warning.invalid_cover", cover.id));
            return;
        }

        ItemStack stack = cover.getCoverStack();

        if (!coverable.canPlaceCoverOnSide(def, side)) {
            ctx.warn(Component.translatable("mm.info.warning.could_not_place_cover", stack.getHoverName()));
            return;
        }

        if (!ctx.tryApplyAction(1)) {
            ctx.error(Component.translatable("mm.info.error.out_of_eu"));
            return;
        }

        if (!ctx.tryConsumeItems(stack.copy())) {
            ctx.warn(Component.translatable("mm.info.warning.could_not_find_cover", stack.getHoverName()));
            return;
        }

        if (!coverable.placeCoverOnSide(side, stack, def, player)) {
            ctx.givePlayerItems(stack);
            return;
        }

        CoverBehavior placed = coverable.getCoverAtSide(side);

        if (placed != null && cover.data != null && !cover.data.isEmpty() && player != null) {
            placed.pasteConfig(player, cover.data.copy());
        }
    }

    private void removeCover(IBlockApplyContext ctx, ICoverable coverable, Direction side) {
        if (!ctx.tryApplyAction(1)) {
            ctx.error(Component.translatable("mm.info.error.out_of_eu"));
            return;
        }

        // the drops are popped into the world and captured by the apply context
        coverable.removeCover(true, side, null);
    }

    @Override
    public boolean getRequiredItemsForExistingBlock(IBlockApplyContext context) {
        BlockEntity te = context.getTileEntity();

        ICoverable coverable = null;

        if (te instanceof PipeBlockEntity<?, ?> pipe) coverable = pipe.getCoverContainer();
        if (te instanceof IMachineBlockEntity mbe) coverable = mbe.getMetaMachine().getCoverContainer();

        for (Direction dir : Direction.values()) {
            CoverData target = mCovers == null ? null : mCovers[dir.ordinal()];
            CoverBehavior actual = coverable == null ? null : coverable.getCoverAtSide(dir);

            if (actual != null && (target == null || !ItemStack.isSameItemSameTags(actual.getAttachItem(), target.getCoverStack()))) {
                context.givePlayerItems(actual.getAttachItem().copyWithCount(1));
                actual = null;
            }

            if (actual == null && target != null) {
                context.tryConsumeItems(target.getCoverStack());
            }
        }

        return true;
    }

    @Override
    public boolean getRequiredItemsForNewBlock(IBlockApplyContext context) {
        if (mCovers != null) {
            for (CoverData cover : mCovers) {
                if (cover != null) context.tryConsumeItems(cover.getCoverStack());
            }
        }

        return true;
    }

    @Override
    public void getItemTag(ItemStack stack) {}

    @Override
    public void getItemDetailsChat(List<Component> details) {
        if (mGTGhostCircuit > 0) {
            details.add(Component.translatable("mm.info.ghost_circuit", mGTGhostCircuit));
        }
    }

    @Override
    public void transform(Transform transform) {
        if (mConnections != -1) mConnections = transform.applyBits(mConnections) & 0xFF;
        mBlockedConnections = transform.applyBits(mBlockedConnections) & 0xFF;

        mItemOutput = transform.apply(mItemOutput);
        mFluidOutput = transform.apply(mFluidOutput);

        if (mCovers != null) {
            CoverData[] out = new CoverData[6];

            for (Direction dir : Direction.values()) {
                out[transform.apply(dir).ordinal()] = mCovers[dir.ordinal()];
            }

            mCovers = out;
        }
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public GTAnalysisResult clone() {
        GTAnalysisResult dup = new GTAnalysisResult();

        dup.mConnections = mConnections;
        dup.mBlockedConnections = mBlockedConnections;
        dup.mGTColour = mGTColour;
        dup.mWorkingEnabled = mWorkingEnabled;
        dup.mItemOutput = mItemOutput;
        dup.mFluidOutput = mFluidOutput;
        dup.mAutoOutputItems = mAutoOutputItems;
        dup.mAutoOutputFluids = mAutoOutputFluids;
        dup.mAllowInputFromOutputItems = mAllowInputFromOutputItems;
        dup.mAllowInputFromOutputFluids = mAllowInputFromOutputFluids;
        dup.mMuffled = mMuffled;
        dup.mDistinct = mDistinct;
        dup.mVoidingMode = mVoidingMode;
        dup.mBatchEnabled = mBatchEnabled;
        dup.mActiveRecipeType = mActiveRecipeType;
        dup.mGTGhostCircuit = mGTGhostCircuit;

        if (mCovers != null) {
            dup.mCovers = new CoverData[6];
            for (int i = 0; i < 6; i++) dup.mCovers[i] = mCovers[i] == null ? null : mCovers[i].clone();
        }

        dup.mGTData = mGTData == null ? null : mGTData.copy();

        return dup;
    }

    @Override
    public void migrate() {}

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GTAnalysisResult other)) return false;
        return mConnections == other.mConnections && mBlockedConnections == other.mBlockedConnections && mGTColour == other.mGTColour &&
            Objects.equals(mWorkingEnabled, other.mWorkingEnabled) && mItemOutput == other.mItemOutput && mFluidOutput == other.mFluidOutput &&
            mAutoOutputItems == other.mAutoOutputItems && mAutoOutputFluids == other.mAutoOutputFluids &&
            mAllowInputFromOutputItems == other.mAllowInputFromOutputItems && mAllowInputFromOutputFluids == other.mAllowInputFromOutputFluids &&
            Objects.equals(mMuffled, other.mMuffled) && Objects.equals(mDistinct, other.mDistinct) &&
            Objects.equals(mVoidingMode, other.mVoidingMode) && Objects.equals(mBatchEnabled, other.mBatchEnabled) &&
            mActiveRecipeType == other.mActiveRecipeType && mGTGhostCircuit == other.mGTGhostCircuit &&
            java.util.Arrays.equals(mCovers, other.mCovers) && Objects.equals(mGTData, other.mGTData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mConnections, mBlockedConnections, mGTColour, mWorkingEnabled, mItemOutput, mFluidOutput, mMuffled, mDistinct,
            mVoidingMode, mBatchEnabled, mActiveRecipeType, mGTGhostCircuit, java.util.Arrays.hashCode(mCovers), mGTData);
    }
}
