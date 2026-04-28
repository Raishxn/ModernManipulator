package com.raishxn.modern_manipulator.common.item;

import com.gregtechceu.gtceu.api.item.capability.ElectricItem;

import net.minecraft.world.item.ItemStack;

public class ManipulatorElectricItem extends ElectricItem {

    private final long transferLimit;

    public ManipulatorElectricItem(ItemStack itemStack, long maxCharge, int tier, long transferLimit) {
        super(itemStack, maxCharge, tier, true, false);
        this.transferLimit = transferLimit;
    }

    @Override
    public long getTransferLimit() {
        return transferLimit;
    }

    @Override
    public long charge(long amount, int chargerTier, boolean ignoreTransferLimit, boolean simulate) {
        if (itemStack.getCount() != 1 || !chargeable() || amount <= 0L) {
            return 0L;
        }

        long canReceive = getMaxCharge() - getCharge();
        if (!ignoreTransferLimit) {
            amount = Math.min(amount, getTransferLimit());
        }

        long charged = Math.min(amount, canReceive);
        if (!simulate) {
            setCharge(getCharge() + charged);
        }
        return charged;
    }
}
