package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.google.gson.annotations.SerializedName;
import com.raishxn.modern_manipulator.common.building.providers.IItemProvider;

import java.util.Objects;

/**
 * An item stack that can be moved between worlds without id shifting.
 */
public class PortableItemStack implements IItemProvider {

    @SerializedName("id")
    public String item;
    @SerializedName("a")
    public Integer amount;
    @SerializedName("nbt")
    public CompoundTag nbt;

    public transient Item itemRef;

    public PortableItemStack() {}

    public PortableItemStack(Item item) {
        this.item = BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public PortableItemStack(ItemStack stack) {
        item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        amount = stack.getCount() == 1 ? null : stack.getCount();
    }

    public static PortableItemStack withoutStackSize(ItemStack stack) {
        stack = stack.copy();
        stack.setCount(1);
        return new PortableItemStack(stack);
    }

    public static PortableItemStack withNBT(ItemStack stack) {
        PortableItemStack portable = new PortableItemStack(stack);
        portable.nbt = stack.getTag() == null || stack.getTag().isEmpty() ? null : stack.getTag().copy();
        return portable;
    }

    public Item getItem() {
        if (itemRef == null) {
            itemRef = item == null ? Items.AIR : BuiltInRegistries.ITEM.get(new ResourceLocation(item));
        }

        return itemRef;
    }

    public ItemStack toStack() {
        Item item = getItem();

        if (item == Items.AIR) return ItemStack.EMPTY;

        ItemStack stack = new ItemStack(item, amount == null ? 1 : amount);

        if (nbt != null) stack.setTag(nbt.copy());

        return stack;
    }

    @Override
    public ItemStack getStack(IPseudoInventory inv, boolean consume) {
        ItemStack stack = toStack();

        if (!consume) return stack;

        if (!inv.tryConsumeItems(stack)) return null;

        return stack;
    }

    @Override
    public String toString() {
        return toStack().getHoverName().getString() + (amount == null || amount == 1 ? "" : " x " + amount);
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public PortableItemStack clone() {
        PortableItemStack dup = new PortableItemStack();

        dup.item = item;
        dup.amount = amount;
        dup.nbt = nbt == null ? null : nbt.copy();
        dup.itemRef = itemRef;

        return dup;
    }

    @Override
    public int hashCode() {
        return Objects.hash(item, amount, nbt);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PortableItemStack other)) return false;
        return Objects.equals(item, other.item) && Objects.equals(amount, other.amount) &&
                Objects.equals(nbt, other.nbt);
    }
}
