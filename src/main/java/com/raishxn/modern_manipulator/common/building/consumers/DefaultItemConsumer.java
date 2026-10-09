package com.raishxn.modern_manipulator.common.building.consumers;

import static com.raishxn.modern_manipulator.common.building.IPseudoInventory.CONSUME_PARTIAL;

import com.raishxn.modern_manipulator.common.building.IPseudoInventory;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;

import java.util.Collections;
import java.util.List;

public class DefaultItemConsumer implements IItemConsumer {

    @Override
    public void consume(IPseudoInventory inv, BigItemStack in, BigItemStack out, int flags) {
        List<BigItemStack> extractedStacks = inv.tryConsumeItems(Collections.singletonList(in.copy()), CONSUME_PARTIAL | flags).right();

        if (extractedStacks != null && !extractedStacks.isEmpty()) {
            long amount = 0;
            for (BigItemStack stack : extractedStacks) amount += stack.getStackSize();

            in.decStackSize(amount);
            out.incStackSize(amount);
        }
    }
}
