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
}
