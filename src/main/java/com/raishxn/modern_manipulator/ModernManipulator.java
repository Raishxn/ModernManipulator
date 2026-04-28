package com.raishxn.modern_manipulator;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.raishxn.modern_manipulator.common.item.MMCreativeTabs;
import com.raishxn.modern_manipulator.common.item.MMItems;
import com.raishxn.modern_manipulator.common.recipe.MMRecipes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ModernManipulator.MOD_ID)
@SuppressWarnings("removal")
public class ModernManipulator {

    public static final String MOD_ID = "matter_manipulator";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public ModernManipulator() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        MMItems.ITEMS.register(modEventBus);
        MMCreativeTabs.TABS.register(modEventBus);
        MMRecipes.SERIALIZERS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
        REGISTRATE.registerRegistrate();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> LOGGER.info("Matter Manipulator modern port initialized."));
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
