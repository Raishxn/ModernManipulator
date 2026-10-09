package com.raishxn.modern_manipulator.common.utils;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * An item stack with a long stack size.
 */
public class BigItemStack {

    private ItemId id;
    public long stackSize;

    public BigItemStack(ItemId id, long stackSize) {
        this.id = id;
        this.stackSize = stackSize;
    }

    public ItemId getId() {
        return id;
    }

    public ItemStack getItemStack() {
        return id.getItemStack((int) Math.min(Integer.MAX_VALUE, stackSize));
    }

    public static BigItemStack create(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return new BigItemStack(ItemId.create(stack), stack.getCount());
    }

    public static BigItemStack create(ItemId id, long amount) {
        return new BigItemStack(id, amount);
    }

    /** Removes up to stackSize items and returns them as a normal stack. */
    public ItemStack remove(int stackSize) {
        int toRemove = (int) Math.min(this.stackSize, stackSize);
        this.stackSize -= toRemove;
        return id.getItemStack(toRemove);
    }

    public BigItemStack removeBig(long stackSize) {
        long toRemove = Math.min(this.stackSize, stackSize);
        this.stackSize -= toRemove;
        return new BigItemStack(id, toRemove);
    }

    public BigItemStack incStackSize(long stackSize) {
        this.stackSize += stackSize;
        return this;
    }

    public BigItemStack decStackSize(long stackSize) {
        this.stackSize -= stackSize;
        return this;
    }

    public List<ItemStack> toStacks() {
        return toStacks(id.getItemStack().getMaxStackSize());
    }

    public List<ItemStack> toStacks(int maxStackSize) {
        List<ItemStack> out = new ArrayList<>();
        long remaining = stackSize;

        while (remaining > 0) {
            int amount = (int) Math.min(remaining, maxStackSize);
            out.add(id.getItemStack(amount));
            remaining -= amount;
        }

        return out;
    }

    public BigItemStack copy() {
        return new BigItemStack(id, stackSize);
    }

    public BigItemStack setStackSize(long stackSize) {
        this.stackSize = stackSize;
        return this;
    }

    public long getStackSize() {
        return stackSize;
    }

    public Item getItem() {
        return id.item();
    }

    public boolean isSameType(ItemStack other) {
        return id.isSameAs(other);
    }

    public boolean isSameType(BigItemStack other) {
        return id.equals(other.id);
    }

    @Override
    public String toString() {
        return stackSize + "x" + id;
    }
}
