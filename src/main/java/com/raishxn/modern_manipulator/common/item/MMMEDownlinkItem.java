package com.raishxn.modern_manipulator.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.raishxn.modern_manipulator.common.integration.ae2.AE2Integration;
import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MMMEDownlinkItem extends MMComponentItem {

    public MMMEDownlinkItem() {
        super(null);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }

        ItemStack manipulatorStack = player
                .getItemInHand(context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ?
                        net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        if (!(manipulatorStack.getItem() instanceof MatterManipulatorItem manipulator)) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.downlink.no_manipulator"),
                    true);
            return InteractionResult.FAIL;
        }

        MMState state = MatterManipulatorItem.getState(manipulatorStack);
        if (!state.hasCapability(manipulator.tier(), MMCapability.CONNECTS_TO_AE)) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.downlink.no_capability"),
                    true);
            return InteractionResult.FAIL;
        }

        MarkedPosition downlink = new MarkedPosition(level.dimension().location(), context.getClickedPos());
        if (!AE2Integration.hasGridNode(player, downlink)) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.downlink.no_grid"), true);
            return InteractionResult.FAIL;
        }

        state.setMeDownlink(downlink);
        MatterManipulatorItem.setState(manipulatorStack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.downlink.linked",
                downlink.shortText()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.matter_manipulator.downlink")
                .withStyle(ChatFormatting.GRAY));
    }
}
