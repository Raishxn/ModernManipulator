package com.raishxn.modern_manipulator.common.compat.ae;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.implementations.items.IFacadeItem;
import appeng.api.parts.IFacadePart;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.util.SettingsFrom;
import com.raishxn.modern_manipulator.common.building.IBlockApplyContext;
import com.raishxn.modern_manipulator.common.building.ITileAnalysisIntegration;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The AE2 settings of a block: the parts of a cable bus (cable, busses, panels...), its facades and the memory card
 * settings of AE machines.
 */
public class AEAnalysisResult implements ITileAnalysisIntegration {

    /** Index 6 is the cable (center) slot. */
    public static final int CENTER = 6;

    public static class AEPartData {

        public String mPart;
        public CompoundTag mSettings;

        public AEPartData() {}

        public AEPartData(IPart part) {
            mPart = BuiltInRegistries.ITEM.getKey(part.getPartItem().asItem()).toString();

            CompoundTag tag = new CompoundTag();
            part.exportSettings(SettingsFrom.MEMORY_CARD, tag);

            mSettings = tag.isEmpty() ? null : tag;
        }

        public AEPartData(Item partItem) {
            mPart = BuiltInRegistries.ITEM.getKey(partItem).toString();
        }

        public Item getItem() {
            return BuiltInRegistries.ITEM.get(new ResourceLocation(mPart));
        }

        public ItemStack getStack() {
            return new ItemStack(getItem());
        }

        @Override
        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public AEPartData clone() {
            AEPartData dup = new AEPartData();
            dup.mPart = mPart;
            dup.mSettings = mSettings == null ? null : mSettings.copy();
            return dup;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof AEPartData other && Objects.equals(mPart, other.mPart) &&
                    Objects.equals(mSettings, other.mSettings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPart, mSettings);
        }
    }

    public AEPartData[] mAEParts = null;
    public String[] mAEFacades = null;
    /** Memory card settings of a non-part AE machine */
    public CompoundTag mAEMachineSettings = null;

    public static @Nullable AEAnalysisResult analyze(BlockEntity te, @Nullable Player player) {
        AEAnalysisResult result = new AEAnalysisResult();

        if (te instanceof IPartHost host) {
            AEPartData[] parts = new AEPartData[7];
            String[] facades = new String[6];

            boolean hasPart = false, hasFacade = false;

            for (int i = 0; i < 7; i++) {
                IPart part = host.getPart(i == CENTER ? null : Direction.from3DDataValue(i));

                if (part != null) {
                    parts[i] = new AEPartData(part);
                    hasPart = true;
                }

                if (i < 6) {
                    IFacadePart facade = host.getFacadeContainer().getFacade(Direction.from3DDataValue(i));

                    if (facade != null) {
                        facades[i] = facade.getItemStack().save(new CompoundTag()).toString();
                        hasFacade = true;
                    }
                }
            }

            if (hasPart) result.mAEParts = parts;
            if (hasFacade) result.mAEFacades = facades;
        } else if (te instanceof AEBaseBlockEntity aebe) {
            CompoundTag tag = new CompoundTag();

            try {
                aebe.exportSettings(SettingsFrom.MEMORY_CARD, tag, player);
            } catch (Throwable ignored) {}

            if (!tag.isEmpty()) result.mAEMachineSettings = tag;
        } else {
            return null;
        }

        if (result.mAEParts == null && result.mAEFacades == null && result.mAEMachineSettings == null) return null;

        return result;
    }

    private static Direction side(int i) {
        return i == CENTER ? null : Direction.from3DDataValue(i);
    }

    private static ItemStack facadeStack(String snbt) {
        try {
            return ItemStack.of(net.minecraft.nbt.TagParser.parseTag(snbt));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public boolean apply(IBlockApplyContext ctx) {
        BlockEntity te = ctx.getTileEntity();
        Player player = ctx.getRealPlayer();

        if (te instanceof IPartHost host) {
            // remove parts that shouldn't exist first (the cable is removed last and added first)
            for (int i = 0; i < 7; i++) {
                Direction side = side(i);

                IPart actual = host.getPart(side);
                AEPartData expected = mAEParts == null ? null : mAEParts[i];

                if (actual != null && (expected == null || actual.getPartItem().asItem() != expected.getItem())) {
                    if (i == CENTER && expected == null && hasAnyNonCenter()) continue;

                    removePart(ctx, host, actual);
                }
            }

            for (int i : new int[] { CENTER, 0, 1, 2, 3, 4, 5 }) {
                Direction side = side(i);

                AEPartData expected = mAEParts == null ? null : mAEParts[i];

                if (expected == null) continue;

                IPart actual = host.getPart(side);

                if (actual == null) {
                    if (!(expected.getItem() instanceof IPartItem<?> partItem)) {
                        ctx.warn(Component.translatable("mm.info.warning.invalid_ae_part", expected.mPart));
                        continue;
                    }

                    if (!host.canAddPart(expected.getStack(), side)) {
                        ctx.warn(Component.translatable("mm.info.warning.could_not_place_ae_part",
                                expected.getStack().getHoverName()));
                        continue;
                    }

                    if (!ctx.tryConsumeItems(expected.getStack())) {
                        ctx.warn(Component.translatable("mm.info.warning.could_not_find_ae_part",
                                expected.getStack().getHoverName()));
                        continue;
                    }

                    actual = host.addPart(partItem, side, player);

                    if (actual == null) {
                        ctx.givePlayerItems(expected.getStack());
                        continue;
                    }
                }

                if (expected.mSettings != null) {
                    CompoundTag current = new CompoundTag();
                    actual.exportSettings(SettingsFrom.MEMORY_CARD, current);

                    if (!current.equals(expected.mSettings)) {
                        if (!ctx.tryApplyAction(1)) return false;

                        actual.importSettings(SettingsFrom.MEMORY_CARD, expected.mSettings.copy(), player);
                    }
                }
            }

            // now remove the cable if needed
            IPart cable = host.getPart(null);
            if (cable != null && (mAEParts == null || mAEParts[CENTER] == null) && !hasAnyNonCenter()) {
                removePart(ctx, host, cable);
            }

            // facades
            if (host.getBlockEntity() != null && !host.getBlockEntity().isRemoved()) {
                for (int i = 0; i < 6; i++) {
                    Direction side = Direction.from3DDataValue(i);

                    IFacadePart actual = host.getFacadeContainer().getFacade(side);
                    ItemStack expected = mAEFacades == null || mAEFacades[i] == null ? ItemStack.EMPTY :
                            facadeStack(mAEFacades[i]);

                    if (actual != null &&
                            (expected.isEmpty() || !ItemStack.isSameItemSameTags(actual.getItemStack(), expected))) {
                        ctx.givePlayerItems(actual.getItemStack().copy());
                        host.getFacadeContainer().removeFacade(host, side);
                        actual = null;
                    }

                    if (actual == null && !expected.isEmpty() && expected.getItem() instanceof IFacadeItem facadeItem) {
                        IFacadePart facade = facadeItem.createPartFromItemStack(expected, side);

                        if (facade != null && host.getFacadeContainer().canAddFacade(facade)) {
                            if (ctx.tryConsumeItems(expected.copy())) {
                                host.getFacadeContainer().addFacade(facade);
                            } else {
                                ctx.warn(Component.translatable("mm.info.warning.could_not_find_ae_part",
                                        expected.getHoverName()));
                            }
                        }
                    }
                }

                host.markForUpdate();
                host.markForSave();
            }

            return true;
        }

        if (te instanceof AEBaseBlockEntity aebe && mAEMachineSettings != null) {
            CompoundTag current = new CompoundTag();

            try {
                aebe.exportSettings(SettingsFrom.MEMORY_CARD, current, player);
            } catch (Throwable ignored) {}

            if (!current.equals(mAEMachineSettings)) {
                if (!ctx.tryApplyAction(1)) return false;

                aebe.importSettings(SettingsFrom.MEMORY_CARD, mAEMachineSettings.copy(), player);
            }
        }

        return true;
    }

    private boolean hasAnyNonCenter() {
        if (mAEParts == null) return false;

        for (int i = 0; i < 6; i++) {
            if (mAEParts[i] != null) return true;
        }

        return false;
    }

    private static void removePart(IBlockApplyContext ctx, IPartHost host, IPart part) {
        List<ItemStack> drops = new ArrayList<>();
        part.addPartDrop(drops, false);
        part.addAdditionalDrops(drops, false);

        host.removePart(part);

        ctx.givePlayerItems(drops.toArray(new ItemStack[0]));
    }

    @Override
    public boolean getRequiredItemsForExistingBlock(IBlockApplyContext context) {
        BlockEntity te = context.getTileEntity();

        IPartHost host = te instanceof IPartHost h ? h : null;

        if (mAEParts != null) {
            for (int i = 0; i < 7; i++) {
                if (mAEParts[i] == null) continue;

                IPart actual = host == null ? null : host.getPart(side(i));

                if (actual == null || actual.getPartItem().asItem() != mAEParts[i].getItem()) {
                    context.tryConsumeItems(mAEParts[i].getStack());
                }
            }
        }

        return true;
    }

    @Override
    public boolean getRequiredItemsForNewBlock(IBlockApplyContext context) {
        if (mAEParts != null) {
            for (AEPartData part : mAEParts) {
                if (part != null) context.tryConsumeItems(part.getStack());
            }
        }

        if (mAEFacades != null) {
            for (String facade : mAEFacades) {
                if (facade != null) context.tryConsumeItems(facadeStack(facade));
            }
        }

        return true;
    }

    @Override
    public ItemStack getPreviewStack() {
        if (mAEParts == null) return null;

        if (mAEParts[CENTER] != null) return mAEParts[CENTER].getStack();

        for (AEPartData part : mAEParts) {
            if (part != null) return part.getStack();
        }

        return null;
    }

    @Override
    public void getItemTag(ItemStack stack) {}

    @Override
    public void getItemDetailsChat(List<Component> details) {}

    @Override
    public void transform(Transform transform) {
        if (mAEParts != null) {
            AEPartData[] out = new AEPartData[7];
            out[CENTER] = mAEParts[CENTER];

            for (int i = 0; i < 6; i++) {
                out[transform.apply(Direction.from3DDataValue(i)).ordinal()] = mAEParts[i];
            }

            mAEParts = out;
        }

        if (mAEFacades != null) {
            String[] out = new String[6];

            for (int i = 0; i < 6; i++) {
                out[transform.apply(Direction.from3DDataValue(i)).ordinal()] = mAEFacades[i];
            }

            mAEFacades = out;
        }
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public AEAnalysisResult clone() {
        AEAnalysisResult dup = new AEAnalysisResult();

        if (mAEParts != null) {
            dup.mAEParts = new AEPartData[7];
            for (int i = 0; i < 7; i++) dup.mAEParts[i] = mAEParts[i] == null ? null : mAEParts[i].clone();
        }

        dup.mAEFacades = mAEFacades == null ? null : mAEFacades.clone();
        dup.mAEMachineSettings = mAEMachineSettings == null ? null : mAEMachineSettings.copy();

        return dup;
    }

    @Override
    public void migrate() {}

    @Override
    public boolean equals(Object o) {
        return o instanceof AEAnalysisResult other && Arrays.equals(mAEParts, other.mAEParts) &&
                Arrays.equals(mAEFacades, other.mAEFacades) &&
                Objects.equals(mAEMachineSettings, other.mAEMachineSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mAEParts), Arrays.hashCode(mAEFacades), mAEMachineSettings);
    }
}
