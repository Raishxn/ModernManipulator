package com.raishxn.modern_manipulator.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MMComponentItem extends Item {

    private final @Nullable MMUpgrade upgrade;

    public MMComponentItem(@Nullable MMUpgrade upgrade) {
        super(new Item.Properties());
        this.upgrade = upgrade;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (upgrade != null) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.upgrade").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.matter_manipulator.upgrade_allowed_tiers",
                    upgrade.allowedTiersText()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
