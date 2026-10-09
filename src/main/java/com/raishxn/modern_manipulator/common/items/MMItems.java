package com.raishxn.modern_manipulator.common.items;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class MMItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModernManipulator.MOD_ID);

    private static final List<RegistryObject<Item>> MANIPULATORS = new ArrayList<>();
    private static final List<RegistryObject<Item>> COMPONENTS = new ArrayList<>();

    public static final RegistryObject<Item> MK0 = manipulator("prototype_matter_manipulator", ManipulatorTier.Tier0);
    public static final RegistryObject<Item> MK1 = manipulator("matter_manipulator_mk1", ManipulatorTier.Tier1);
    public static final RegistryObject<Item> MK2 = manipulator("matter_manipulator_mk2", ManipulatorTier.Tier2);
    public static final RegistryObject<Item> MK3 = manipulator("matter_manipulator_mk3", ManipulatorTier.Tier3);

    public static final RegistryObject<Item> HOLOGRAM = component("matter_manipulator_plan", 0);
    public static final RegistryObject<Item> POWER_CORE_0 = component("prototype_power_core", 1);
    public static final RegistryObject<Item> COMPUTER_CORE_0 = component("prototype_computer_core", 2);
    public static final RegistryObject<Item> TELEPORTER_CORE_0 = component("prototype_teleporter_core", 3);
    public static final RegistryObject<Item> FRAME_0 = component("prototype_frame", 4);
    public static final RegistryObject<Item> LENS_0 = component("prototype_lens_assembly", 5);
    public static final RegistryObject<Item> POWER_CORE_1 = component("power_core_mk1", 6);
    public static final RegistryObject<Item> COMPUTER_CORE_1 = component("computer_core_mk1", 7);
    public static final RegistryObject<Item> TELEPORTER_CORE_1 = component("teleporter_core_mk1", 8);
    public static final RegistryObject<Item> FRAME_1 = component("frame_mk1", 9);
    public static final RegistryObject<Item> LENS_1 = component("lens_assembly_mk1", 10);
    public static final RegistryObject<Item> POWER_CORE_2 = component("power_core_mk2", 11);
    public static final RegistryObject<Item> COMPUTER_CORE_2 = component("computer_core_mk2", 12);
    public static final RegistryObject<Item> TELEPORTER_CORE_2 = component("teleporter_core_mk2", 13);
    public static final RegistryObject<Item> FRAME_2 = component("frame_mk2", 14);
    public static final RegistryObject<Item> LENS_2 = component("lens_assembly_mk2", 15);
    public static final RegistryObject<Item> POWER_CORE_3 = component("power_core_mk3", 16);
    public static final RegistryObject<Item> COMPUTER_CORE_3 = component("computer_core_mk3", 17);
    public static final RegistryObject<Item> TELEPORTER_CORE_3 = component("teleporter_core_mk3", 18);
    public static final RegistryObject<Item> FRAME_3 = component("frame_mk3", 19);
    public static final RegistryObject<Item> LENS_3 = component("lens_assembly_mk3", 20);
    public static final RegistryObject<Item> AE_DOWNLINK = component("me_downlink", 21);
    public static final RegistryObject<Item> QUANTUM_DOWNLINK = component("quantum_downlink", 22);
    public static final RegistryObject<Item> BLANK_UPGRADE = component("blank_upgrade", 23);
    public static final RegistryObject<Item> ENERGY_TUNNEL_UPGRADE = component("energy_tunnel_upgrade", 24);
    public static final RegistryObject<Item> EXCAVATION_UPGRADE = component("excavation_upgrade", 25);
    public static final RegistryObject<Item> AUXILIARY_TELEPORTER_UPGRADE = component("auxiliary_teleporter_upgrade", 26);
    public static final RegistryObject<Item> ADAPTIVE_WIRING_HARNESS_UPGRADE = component("adaptive_wiring_harness_upgrade", 27);

    private MMItems() {}

    public static void addCreativeTabItems(CreativeModeTab.Output output) {
        for (RegistryObject<Item> item : MANIPULATORS) {
            ItemMatterManipulator manipulator = (ItemMatterManipulator) item.get();

            output.accept(manipulator.getDefaultInstance());
            output.accept(manipulator.createChargedStack());
        }

        COMPONENTS.forEach(item -> output.accept(item.get()));

        // the uplink machines are registered through GTRegistrate, which doesn't add them to this tab
        output.accept(com.raishxn.modern_manipulator.common.uplink.MMUplinkMachines.UPLINK.asStack());
        output.accept(com.raishxn.modern_manipulator.common.uplink.MMUplinkMachines.UPLINK_HATCH.asStack());
    }

    private static RegistryObject<Item> manipulator(String name, ManipulatorTier tier) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new ItemMatterManipulator(tier));
        MANIPULATORS.add(item);
        return item;
    }

    private static RegistryObject<Item> component(String name, int id) {
        RegistryObject<Item> item = ITEMS.register(name, () -> new MetaItem(id));
        COMPONENTS.add(item);
        return item;
    }
}
