package com.raishxn.modern_manipulator.common.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The crafting components and upgrades. Each original meta item is its own item now.
 */
public class MetaItem extends Item {

    /** The original meta item id, used for the lang keys. */
    public final int id;

    public MetaItem(int id) {
        super(new Item.Properties());
        this.id = id;
    }

    @Override
    public String getDescriptionId() {
        return "item.metaitem." + id + ".name";
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = "item.metaitem." + id + ".desc";

        if (net.minecraft.locale.Language.getInstance().has(key)) {
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }
}
