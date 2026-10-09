package com.raishxn.modern_manipulator.common.networking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

import com.raishxn.modern_manipulator.common.building.BlockAnalyzer;
import com.raishxn.modern_manipulator.common.building.BlockAnalyzer.RequiredItemAnalysis;
import com.raishxn.modern_manipulator.common.building.IPseudoInventory;
import com.raishxn.modern_manipulator.common.building.MMInventory;
import com.raishxn.modern_manipulator.common.building.MMItemConsumer;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.PLAN_ALL;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.PLAN_AUTO_SUBMIT;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendInfoToPlayer;

public class PlanHelper {

    private PlanHelper() {}

    /**
     * The logic for creating a plan.
     */
    public static void createPlanImpl(Player player, MMState state, ItemMatterManipulator manipulator, int flags) {
        state = state.clone();

        if (!Location.areCompatible(state.config.coordA, state.config.coordB)) {
            sendErrorToPlayer(player, "mm.info.error.must_have_copy_region");
            return;
        }

        if ((flags & PLAN_ALL) != 0) {
            if (!Location.areCompatible(state.config.coordA, state.config.coordC)) {
                state.config.coordC = state.config.coordA.clone();
            }
        } else {
            if (!Location.areCompatible(state.config.coordA, state.config.coordC)) {
                sendErrorToPlayer(player, "mm.info.error.must_have_paste_region");
                return;
            }
        }

        List<PendingBlock> blocks = state.getPendingBlocks(manipulator.tier, player.level());
        RequiredItemAnalysis itemAnalysis = BlockAnalyzer.getRequiredItemsForBuild(player, blocks,
                (flags & PLAN_ALL) != 0);

        List<BigItemStack> requiredItems = MMUtils
                .mapToList(itemAnalysis.requiredItems.entrySet(), e -> BigItemStack.create(e.getKey(), e.getValue()));

        MMInventory inv = new MMInventory(player, state, manipulator.tier);

        List<BigItemStack> availableItems = new ArrayList<>();

        for (BigItemStack requiredItem : requiredItems) {
            BigItemStack availableItem = MMItemConsumer
                    .consume(inv, requiredItem.copy(),
                            IPseudoInventory.CONSUME_SIMULATED | IPseudoInventory.CONSUME_IGNORE_CREATIVE);

            if (availableItem != null) availableItems.add(availableItem);
        }

        sendInfoToPlayer(player, "mm.info.required_items");

        if (!requiredItems.isEmpty()) {
            requiredItems.stream()
                    .map((BigItemStack stack) -> {
                        long available = availableItems.stream()
                                .filter(s -> s.isSameType(stack))
                                .mapToLong(BigItemStack::getStackSize)
                                .sum();

                        MutableComponent name = stack.getItemStack().getHoverName().copy();

                        if (stack.getStackSize() - available > 0) {
                            return Component.translatable(
                                    "mm.info.missing",
                                    name,
                                    Component.literal(MMUtils.formatNumber(stack.getStackSize()))
                                            .withStyle(ChatFormatting.GOLD),
                                    Component.literal(MMUtils.formatNumber(stack.getStackSize() - available))
                                            .withStyle(ChatFormatting.RED));
                        } else {
                            return name.append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(MMUtils.formatNumber(stack.getStackSize()))
                                            .withStyle(ChatFormatting.GOLD));
                        }
                    })
                    .sorted(Comparator.comparing(Component::getString))
                    .forEach(message -> sendInfoToPlayer(player, message));
        } else {
            sendInfoToPlayer(player, "mm.info.none");
        }

        if (!requiredItems.isEmpty()) {
            if (state.connectToUplink()) {
                if ((flags & PLAN_ALL) == 0) {
                    requiredItems.forEach(stack -> {
                        long available = availableItems.stream()
                                .filter(s -> s.isSameType(stack))
                                .mapToLong(BigItemStack::getStackSize)
                                .sum();

                        stack.decStackSize(available);
                    });

                    requiredItems.removeIf(stack -> stack.getStackSize() <= 0);
                }

                if (!requiredItems.isEmpty()) {
                    state.uplink.submitPlan(player, state.config.coordA.toString(), requiredItems,
                            (flags & PLAN_AUTO_SUBMIT) != 0);
                } else {
                    sendInfoToPlayer(player, "mm.info.not_need_creating_pattern");
                }
            } else {
                sendErrorToPlayer(player, "mm.info.error.not_connected");
            }
        }
    }
}
