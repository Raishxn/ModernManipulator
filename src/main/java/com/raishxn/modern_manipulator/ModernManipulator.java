package com.raishxn.modern_manipulator;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.raishxn.modern_manipulator.common.items.MMCreativeTabs;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.items.MMRecipes;
import com.raishxn.modern_manipulator.common.networking.Messages;
import com.raishxn.modern_manipulator.common.utils.Mods;
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
        // the uplink structure uses trinium frames (as in GTNH), which GTCEu doesn't generate
        modEventBus.addListener(
                (com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent event) -> com.gregtechceu.gtceu.common.data.GTMaterials.Trinium
                        .addFlags(
                                com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_FRAME));
        modEventBus.addGenericListener(MachineDefinition.class, this::registerMachines);

        MinecraftForge.EVENT_BUS.register(new CommonEvents());

        REGISTRATE.registerRegistrate();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            Messages.init();

            if (Mods.GregTech.isModLoaded()) com.raishxn.modern_manipulator.common.compat.gt.GTCompat.init();
            if (Mods.AppliedEnergistics2.isModLoaded()) com.raishxn.modern_manipulator.common.compat.ae.AECompat.init();
        });
    }

    private void registerMachines(GTCEuAPI.RegisterEvent<ResourceLocation, MachineDefinition> event) {
        if (Mods.AppliedEnergistics2.isModLoaded()) {
            com.raishxn.modern_manipulator.common.uplink.MMUplinkMachines.init();
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
