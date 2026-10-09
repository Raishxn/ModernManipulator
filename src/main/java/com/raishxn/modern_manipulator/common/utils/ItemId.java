package com.raishxn.modern_manipulator.common.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An immutable item identity (item + nbt), usable as a hash key.
 */
public final class ItemId {

    private final Item item;
    private final @Nullable CompoundTag nbt;
    private final int hash;

    private ItemId(Item item, @Nullable CompoundTag nbt) {
        this.item = item;
        this.nbt = nbt == null || nbt.isEmpty() ? null : nbt;
        this.hash = Objects.hash(item, this.nbt);
    }

    public static ItemId create(ItemStack stack) {
        return new ItemId(stack.getItem(), stack.getTag() == null ? null : stack.getTag().copy());
    }

    public static ItemId createNoCopy(ItemStack stack) {
        return new ItemId(stack.getItem(), stack.getTag());
    }

    public static ItemId create(Item item, @Nullable CompoundTag nbt) {
        return new ItemId(item, nbt == null ? null : nbt.copy());
    }

    public static ItemId createWithoutNBT(ItemStack stack) {
        return new ItemId(stack.getItem(), null);
    }

    public static ItemId create(CompoundTag tag) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(tag.getString("item")));
        return new ItemId(item, tag.contains("nbt") ? tag.getCompound("nbt") : null);
    }

    public CompoundTag writeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("item", BuiltInRegistries.ITEM.getKey(item).toString());
        if (nbt != null) tag.put("nbt", nbt.copy());
        return tag;
    }

    public Item item() {
        return item;
    }

    public @Nullable CompoundTag nbt() {
        return nbt;
    }

    public ItemStack getItemStack() {
        return getItemStack(1);
    }

    public ItemStack getItemStack(int stackSize) {
        ItemStack stack = new ItemStack(item, stackSize);
        if (nbt != null) stack.setTag(nbt.copy());
        return stack;
    }

    public boolean isSameAs(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.getItem() != item) return false;
        CompoundTag other = stack.getTag();
        if (other != null && other.isEmpty()) other = null;
        return Objects.equals(nbt, other);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemId other)) return false;
        return hash == other.hash && item == other.item && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ItemId[" + BuiltInRegistries.ITEM.getKey(item) + (nbt == null ? "" : nbt) + "]";
    }
}
