package com.raishxn.modern_manipulator.common.item;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem.ManipulatorTier;

import java.util.List;

public final class MMItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            ModernManipulator.MOD_ID);

    public static final RegistryObject<Item> PROTOTYPE_MATTER_MANIPULATOR = registerManipulator(
            "prototype_matter_manipulator", ManipulatorTier.PROTOTYPE);
    public static final RegistryObject<Item> MATTER_MANIPULATOR_MK1 = registerManipulator("matter_manipulator_mk1",
            ManipulatorTier.MK1);
    public static final RegistryObject<Item> MATTER_MANIPULATOR_MK2 = registerManipulator("matter_manipulator_mk2",
            ManipulatorTier.MK2);
    public static final RegistryObject<Item> MATTER_MANIPULATOR_MK3 = registerManipulator("matter_manipulator_mk3",
            ManipulatorTier.MK3);

    public static final RegistryObject<Item> MATTER_MANIPULATOR_PLAN = registerComponent("matter_manipulator_plan");
    public static final RegistryObject<Item> PROTOTYPE_POWER_CORE = registerComponent("prototype_power_core");
    public static final RegistryObject<Item> PROTOTYPE_COMPUTER_CORE = registerComponent("prototype_computer_core");
    public static final RegistryObject<Item> PROTOTYPE_TELEPORTER_CORE = registerComponent("prototype_teleporter_core");
    public static final RegistryObject<Item> PROTOTYPE_FRAME = registerComponent("prototype_frame");
    public static final RegistryObject<Item> PROTOTYPE_LENS_ASSEMBLY = registerComponent("prototype_lens_assembly");
    public static final RegistryObject<Item> POWER_CORE_MK1 = registerComponent("power_core_mk1");
    public static final RegistryObject<Item> COMPUTER_CORE_MK1 = registerComponent("computer_core_mk1");
    public static final RegistryObject<Item> TELEPORTER_CORE_MK1 = registerComponent("teleporter_core_mk1");
    public static final RegistryObject<Item> FRAME_MK1 = registerComponent("frame_mk1");
    public static final RegistryObject<Item> LENS_ASSEMBLY_MK1 = registerComponent("lens_assembly_mk1");
    public static final RegistryObject<Item> POWER_CORE_MK2 = registerComponent("power_core_mk2");
    public static final RegistryObject<Item> COMPUTER_CORE_MK2 = registerComponent("computer_core_mk2");
    public static final RegistryObject<Item> TELEPORTER_CORE_MK2 = registerComponent("teleporter_core_mk2");
    public static final RegistryObject<Item> FRAME_MK2 = registerComponent("frame_mk2");
    public static final RegistryObject<Item> LENS_ASSEMBLY_MK2 = registerComponent("lens_assembly_mk2");
    public static final RegistryObject<Item> POWER_CORE_MK3 = registerComponent("power_core_mk3");
    public static final RegistryObject<Item> COMPUTER_CORE_MK3 = registerComponent("computer_core_mk3");
    public static final RegistryObject<Item> TELEPORTER_CORE_MK3 = registerComponent("teleporter_core_mk3");
    public static final RegistryObject<Item> FRAME_MK3 = registerComponent("frame_mk3");
    public static final RegistryObject<Item> LENS_ASSEMBLY_MK3 = registerComponent("lens_assembly_mk3");
    public static final RegistryObject<Item> ME_DOWNLINK = registerComponent("me_downlink");
    public static final RegistryObject<Item> QUANTUM_DOWNLINK = registerComponent("quantum_downlink");
    public static final RegistryObject<Item> BLANK_UPGRADE = registerComponent("blank_upgrade");
    public static final RegistryObject<Item> ENERGY_TUNNEL_UPGRADE = registerComponent("energy_tunnel_upgrade");
    public static final RegistryObject<Item> EXCAVATION_UPGRADE = registerComponent("excavation_upgrade");
    public static final RegistryObject<Item> AUXILIARY_TELEPORTER_UPGRADE = registerComponent(
            "auxiliary_teleporter_upgrade");
    public static final RegistryObject<Item> ADAPTIVE_WIRING_HARNESS_UPGRADE = registerComponent(
            "adaptive_wiring_harness_upgrade");

    private static final List<RegistryObject<Item>> MANIPULATORS = List.of(
            PROTOTYPE_MATTER_MANIPULATOR,
            MATTER_MANIPULATOR_MK1,
            MATTER_MANIPULATOR_MK2,
            MATTER_MANIPULATOR_MK3);

    private static final List<RegistryObject<Item>> COMPONENTS = List.of(
            MATTER_MANIPULATOR_PLAN,
            PROTOTYPE_POWER_CORE,
            PROTOTYPE_COMPUTER_CORE,
            PROTOTYPE_TELEPORTER_CORE,
            PROTOTYPE_FRAME,
            PROTOTYPE_LENS_ASSEMBLY,
            POWER_CORE_MK1,
            COMPUTER_CORE_MK1,
            TELEPORTER_CORE_MK1,
            FRAME_MK1,
            LENS_ASSEMBLY_MK1,
            POWER_CORE_MK2,
            COMPUTER_CORE_MK2,
            TELEPORTER_CORE_MK2,
            FRAME_MK2,
            LENS_ASSEMBLY_MK2,
            POWER_CORE_MK3,
            COMPUTER_CORE_MK3,
            TELEPORTER_CORE_MK3,
            FRAME_MK3,
            LENS_ASSEMBLY_MK3,
            ME_DOWNLINK,
            QUANTUM_DOWNLINK,
            BLANK_UPGRADE,
            ENERGY_TUNNEL_UPGRADE,
            EXCAVATION_UPGRADE,
            AUXILIARY_TELEPORTER_UPGRADE,
            ADAPTIVE_WIRING_HARNESS_UPGRADE);

    private MMItems() {}

    public static void addCreativeTabItems(CreativeModeTab.Output output) {
        MANIPULATORS.forEach(item -> {
            output.accept(item.get());
            if (item.get() instanceof MatterManipulatorItem manipulator) {
                output.accept(manipulator.createChargedStack());
            }
        });
        COMPONENTS.forEach(item -> output.accept(item.get()));
    }

    private static RegistryObject<Item> registerManipulator(String name, ManipulatorTier tier) {
        return ITEMS.register(name, () -> new MatterManipulatorItem(tier));
    }

    private static RegistryObject<Item> registerComponent(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }
}
