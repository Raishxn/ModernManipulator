package com.raishxn.modern_manipulator;

import com.raishxn.modern_manipulator.common.items.MMCreativeTabs;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.items.MMRecipes;
import com.raishxn.modern_manipulator.common.networking.Messages;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ModernManipulator.MOD_ID)
@SuppressWarnings("removal")
public class ModernManipulator {

    public static final String MOD_ID = "modern_manipulator";
    public static final Logger LOG = LogManager.getLogger("ModernManipulator");
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);

    public ModernManipulator() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GlobalMMConfig.register();

        MMItems.ITEMS.register(modEventBus);
        MMCreativeTabs.TABS.register(modEventBus);
        MMRecipes.SERIALIZERS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(new CommonEvents());

        REGISTRATE.registerRegistrate();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(Messages::init);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
