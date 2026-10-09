package com.raishxn.modern_manipulator.common.utils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An immutable fluid identity (fluid + nbt), usable as a hash key.
 */
public final class FluidId {

    private final Fluid fluid;
    private final @Nullable CompoundTag nbt;
    private final int hash;

    private FluidId(Fluid fluid, @Nullable CompoundTag nbt) {
        this.fluid = fluid;
        this.nbt = nbt == null || nbt.isEmpty() ? null : nbt;
        this.hash = Objects.hash(fluid, this.nbt);
    }

    public static FluidId create(FluidStack stack) {
        return new FluidId(stack.getFluid(), stack.getTag() == null ? null : stack.getTag().copy());
    }

    public static FluidId create(Fluid fluid) {
        return new FluidId(fluid, null);
    }

    public Fluid fluid() {
        return fluid;
    }

    public FluidStack getFluidStack() {
        return getFluidStack(1);
    }

    public FluidStack getFluidStack(int amount) {
        return new FluidStack(fluid, amount, nbt == null ? null : nbt.copy());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FluidId other)) return false;
        return hash == other.hash && fluid == other.fluid && Objects.equals(nbt, other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "FluidId[" + BuiltInRegistries.FLUID.getKey(fluid) + (nbt == null ? "" : nbt) + "]";
    }
}
