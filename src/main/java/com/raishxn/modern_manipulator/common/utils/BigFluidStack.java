package com.raishxn.modern_manipulator.common.utils;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A fluid stack with a long amount.
 */
public class BigFluidStack {

    private FluidId id;
    public long amount;

    public BigFluidStack(FluidId id, long amount) {
        this.id = id;
        this.amount = amount;
    }

    public FluidId getId() {
        return id;
    }

    public Fluid getFluid() {
        return id.fluid();
    }

    public FluidStack getFluidStack() {
        return id.getFluidStack((int) Math.min(Integer.MAX_VALUE, amount));
    }

    public static BigFluidStack create(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return new BigFluidStack(FluidId.create(stack), stack.getAmount());
    }

    public static BigFluidStack create(FluidId id, long amount) {
        return new BigFluidStack(id, amount);
    }

    public FluidStack remove(int amount) {
        int toRemove = (int) Math.min(this.amount, amount);
        this.amount -= toRemove;
        return id.getFluidStack(toRemove);
    }

    public BigFluidStack incStackSize(long amount) {
        this.amount += amount;
        return this;
    }

    public BigFluidStack decStackSize(long amount) {
        this.amount -= amount;
        return this;
    }

    public List<FluidStack> toStacks() {
        List<FluidStack> out = new ArrayList<>();
        long remaining = amount;

        while (remaining > 0) {
            int toAdd = (int) Math.min(remaining, Integer.MAX_VALUE);
            out.add(id.getFluidStack(toAdd));
            remaining -= toAdd;
        }

        return out;
    }

    public BigFluidStack copy() {
        return new BigFluidStack(id, amount);
    }

    public BigFluidStack setStackSize(long amount) {
        this.amount = amount;
        return this;
    }

    public long getStackSize() {
        return amount;
    }
}
