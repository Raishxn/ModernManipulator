package com.raishxn.modern_manipulator.common.building;

import com.raishxn.modern_manipulator.common.building.providers.IItemProvider;
import com.raishxn.modern_manipulator.common.building.providers.ItemProviders;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Objects;

/**
 * An analysis for an inventory.
 */
public class InventoryAnalysis {

    public boolean mFuzzy;
    public IItemProvider[] mItems;

    public InventoryAnalysis() {}

    /**
     * Gets an analysis for an inventory.
     *
     * @param fuzzy When true, NBT will be ignored and items will be fuzzily-retrieved.
     */
    public static InventoryAnalysis fromInventory(BlockEntity te, boolean fuzzy) {
        InventoryAdapter adapter = InventoryAdapter.findAdapter(te);

        if (adapter == null) return null;

        var handler = adapter.getHandler(te);

        if (handler == null) return null;

        int size = adapter.getSizeInventory(te);
        if (size == 0) return null;

        InventoryAnalysis analysis = new InventoryAnalysis();

        analysis.mFuzzy = fuzzy;
        analysis.mItems = new IItemProvider[size];

        for (int slot = 0; slot < analysis.mItems.length; slot++) {
            if (!adapter.isValidSlot(te, slot)) continue;

            analysis.mItems[slot] = ItemProviders.getProviderFor(handler.getStackInSlot(slot), fuzzy);
        }

        return analysis;
    }

    /**
     * Applies the analysis
     *
     * @param consume  When true, items will be consumed
     * @param simulate When true, the inventory will not be modified in any way
     * @return True when the inventory was successfully updated
     */
    public boolean apply(IBlockApplyContext context, BlockEntity te, boolean consume, boolean simulate) {
        InventoryAdapter adapter = InventoryAdapter.findAdapter(te);

        if (adapter == null) return true;

        var handler = adapter.getHandler(te);

        if (handler == null) return true;

        if (!adapter.validate(context, te)) return false;

        if (adapter.getSizeInventory(te) != mItems.length) {
            if (adapter.getSizeInventory(te) != 0) {
                context.warn(
                    Component.translatable("mm.info.warning.inventory_was_the_wrong_size", mItems.length, adapter.getSizeInventory(te)));
            }
            return false;
        }

        boolean didSomething = false;
        boolean success = true;

        for (int slot = 0; slot < mItems.length; slot++) {
            if (!adapter.isValidSlot(te, slot)) continue;

            IItemProvider target = mItems[slot];
            IItemProvider actual = ItemProviders.getProviderFor(handler.getStackInSlot(slot), mFuzzy);

            if (!Objects.equals(target, actual)) {
                ItemStack stack = handler.getStackInSlot(slot);

                if (!stack.isEmpty()) {
                    if (!adapter.canExtract(te, slot)) {
                        context.warn(Component.translatable("mm.info.warning.could_not_extract_item_in_slot", slot, stack.getHoverName()));
                        continue;
                    }

                    if (!simulate) {
                        stack = adapter.extract(te, slot);
                        if (!stack.isEmpty()) didSomething = true;
                    }

                    if (!stack.isEmpty() && consume) context.givePlayerItems(stack);
                }

                if (target != null) {
                    ItemStack preview = target.getStack(null, false);

                    if (!adapter.canInsert(te, slot, preview)) {
                        context.warn(Component.translatable("mm.info.warning.invalid_item_for_slot", slot, preview.getHoverName()));
                        continue;
                    }

                    ItemStack toInsert = target.getStack(context, consume);

                    if (toInsert == null) {
                        context.warn(Component.translatable("mm.info.warning.could_not_gather_item_for_inventory", preview.getHoverName()));
                        success = false;
                    } else {
                        if (!simulate) {
                            if (adapter.insert(te, slot, toInsert)) {
                                didSomething = true;
                            } else {
                                context.givePlayerItems(toInsert);
                            }
                        }
                    }
                }
            }
        }

        if (didSomething) te.setChanged();

        return success;
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public InventoryAnalysis clone() {
        InventoryAnalysis dup = new InventoryAnalysis();

        dup.mFuzzy = mFuzzy;
        dup.mItems = new IItemProvider[mItems.length];

        for (int i = 0; i < mItems.length; i++) {
            dup.mItems[i] = mItems[i] == null ? null : mItems[i].clone();
        }

        return dup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFuzzy, java.util.Arrays.hashCode(mItems));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof InventoryAnalysis other)) return false;
        return mFuzzy == other.mFuzzy && java.util.Arrays.equals(mItems, other.mItems);
    }
}
