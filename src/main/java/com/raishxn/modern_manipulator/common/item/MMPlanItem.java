package com.raishxn.modern_manipulator.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.raishxn.modern_manipulator.common.building.BlueprintMaterials;
import com.raishxn.modern_manipulator.common.item.MMState.Blueprint;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MMPlanItem extends Item {

    private static final String TAG_BLUEPRINT = "Blueprint";

    public MMPlanItem() {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack planStack = player.getItemInHand(usedHand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(planStack);
        }

        if (player.isShiftKeyDown()) {
            planStack.getOrCreateTag().remove(TAG_BLUEPRINT);
            player.displayClientMessage(Component.translatable("message.matter_manipulator.plan.cleared"), true);
            return InteractionResultHolder.success(planStack);
        }

        ItemStack manipulatorStack = otherHandStack(player, usedHand);
        if (!(manipulatorStack.getItem() instanceof MatterManipulatorItem)) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.plan.no_manipulator"),
                    true);
            return InteractionResultHolder.fail(planStack);
        }

        Blueprint planBlueprint = getBlueprint(planStack);
        MMState manipulatorState = MatterManipulatorItem.getState(manipulatorStack);
        if (planBlueprint == null) {
            Blueprint manipulatorBlueprint = manipulatorState.blueprint();
            if (manipulatorBlueprint == null) {
                player.displayClientMessage(Component.translatable("message.matter_manipulator.plan.no_blueprint"),
                        true);
                return InteractionResultHolder.fail(planStack);
            }
            setBlueprint(planStack, new Blueprint(manipulatorBlueprint.sizeX(), manipulatorBlueprint.sizeY(),
                    manipulatorBlueprint.sizeZ(), manipulatorBlueprint.blocks(), true));
            player.displayClientMessage(Component.translatable("message.matter_manipulator.plan.saved",
                    manipulatorBlueprint.volume()), true);
            return InteractionResultHolder.success(planStack);
        }

        manipulatorState.setBlueprint(planBlueprint);
        manipulatorState.clearCoordC();
        MatterManipulatorItem.setState(manipulatorStack, manipulatorState);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.plan.loaded",
                planBlueprint.volume()), true);
        return InteractionResultHolder.success(planStack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Blueprint blueprint = getBlueprint(stack);
        if (blueprint == null) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.plan.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.add(Component.translatable("tooltip.matter_manipulator.blueprint", blueprint.sizeX(),
                blueprint.sizeY(), blueprint.sizeZ(), blueprint.volume()).withStyle(ChatFormatting.AQUA));
        appendRequiredItemsTooltip(blueprint, tooltip);
        tooltip.add(Component.translatable("tooltip.matter_manipulator.plan.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void appendRequiredItemsTooltip(Blueprint blueprint, List<Component> tooltip) {
        List<ItemStack> requiredItems = BlueprintMaterials.requiredItems(blueprint);
        if (requiredItems.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.matter_manipulator.required_items")
                .withStyle(ChatFormatting.GRAY));
        int shown = 0;
        for (ItemStack requiredItem : requiredItems) {
            if (shown >= 8) {
                tooltip.add(Component.translatable("tooltip.matter_manipulator.required_items_more",
                        requiredItems.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("tooltip.matter_manipulator.required_item",
                            requiredItem.getHoverName(), requiredItem.getCount()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            shown++;
        }
    }

    public static @Nullable Blueprint getBlueprint(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_BLUEPRINT, Tag.TAG_COMPOUND)) {
            return null;
        }
        return Blueprint.load(tag.getCompound(TAG_BLUEPRINT));
    }

    private static void setBlueprint(ItemStack stack, Blueprint blueprint) {
        stack.getOrCreateTag().put(TAG_BLUEPRINT, blueprint.save());
    }

    private static ItemStack otherHandStack(Player player, InteractionHand hand) {
        return player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND :
                InteractionHand.MAIN_HAND);
    }
}
