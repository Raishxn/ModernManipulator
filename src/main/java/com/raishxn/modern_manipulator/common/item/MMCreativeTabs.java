package com.raishxn.modern_manipulator.common.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.raishxn.modern_manipulator.ModernManipulator;

public final class MMCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB,
            ModernManipulator.MOD_ID);

    public static final RegistryObject<CreativeModeTab> MATTER_MANIPULATOR = TABS.register("matter_manipulator",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.matter_manipulator"))
                    .icon(() -> MMItems.MATTER_MANIPULATOR_MK3.get().getDefaultInstance())
                    .displayItems((parameters, output) -> MMItems.addCreativeTabItems(output))
                    .build());

    private MMCreativeTabs() {}
}
