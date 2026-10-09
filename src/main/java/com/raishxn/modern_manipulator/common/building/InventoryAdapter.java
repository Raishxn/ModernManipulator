package com.raishxn.modern_manipulator.common.building;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Describes how the manipulator can interact with a block entity's inventory.
 * Integrations (GregTech, AE) register their own adapters with a higher priority.
 */
public interface InventoryAdapter {

    List<InventoryAdapter> ADAPTERS = new ArrayList<>();

    static void register(InventoryAdapter adapter) {
        ADAPTERS.add(0, adapter);
    }

    /**
     * Finds the inventory adapter for a block entity.
     *
     * @return The adapter, or null if this block entity's inventory shouldn't be touched.
     */
    static @Nullable InventoryAdapter findAdapter(BlockEntity te) {
        if (te == null) return null;

        for (InventoryAdapter adapter : ADAPTERS) {
            if (adapter.canHandle(te)) return adapter;
        }

        return DEFAULT.canHandle(te) ? DEFAULT : null;
    }

    boolean canHandle(BlockEntity te);

    @Nullable
    IItemHandler getHandler(BlockEntity te);

    default int getSizeInventory(BlockEntity te) {
        IItemHandler handler = getHandler(te);
        return handler == null ? 0 : handler.getSlots();
    }

    default boolean isValidSlot(BlockEntity te, int slot) {
        return true;
    }

    default boolean canExtract(BlockEntity te, int slot) {
        return true;
    }

    default boolean canInsert(BlockEntity te, int slot, ItemStack stack) {
        IItemHandler handler = getHandler(te);
        return handler != null && handler.isItemValid(slot, stack);
    }

    default ItemStack extract(BlockEntity te, int slot) {
        IItemHandler handler = getHandler(te);

        if (handler == null) return ItemStack.EMPTY;

        if (handler instanceof IItemHandlerModifiable modifiable) {
            ItemStack stack = modifiable.getStackInSlot(slot).copy();
            modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            return stack;
        }

        return handler.extractItem(slot, Integer.MAX_VALUE, false);
    }

    default boolean insert(BlockEntity te, int slot, ItemStack stack) {
        IItemHandler handler = getHandler(te);

        if (handler == null) return false;

        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
            return true;
        }

        return handler.insertItem(slot, stack, false).isEmpty();
    }

    default boolean validate(IBlockApplyContext context, BlockEntity te) {
        return true;
    }

    InventoryAdapter DEFAULT = new InventoryAdapter() {

        @Override
        public boolean canHandle(BlockEntity te) {
            return getHandler(te) != null;
        }

        @Override
        public @Nullable IItemHandler getHandler(BlockEntity te) {
            return te.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        }
    };
}
