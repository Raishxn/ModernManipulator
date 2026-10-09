package com.raishxn.modern_manipulator.common.uplink;

import static com.gregtechceu.gtceu.api.pattern.Predicates.abilities;
import static com.gregtechceu.gtceu.api.pattern.Predicates.any;
import static com.gregtechceu.gtceu.api.pattern.Predicates.blocks;
import static com.gregtechceu.gtceu.api.pattern.Predicates.controller;
import static com.gregtechceu.gtceu.api.pattern.Predicates.frames;

import com.raishxn.modern_manipulator.ModernManipulator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.FactoryBlockPattern;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.network.chat.Component;

/**
 * The quantum uplink machines.
 */
public class MMUplinkMachines {

    public static MachineDefinition UPLINK_HATCH;
    public static MultiblockMachineDefinition UPLINK;

    private MMUplinkMachines() {}

    public static void init() {
        UPLINK_HATCH = ModernManipulator.REGISTRATE
            .machine("mm_uplink_me_hatch", MMUplinkMEHatchPartMachine::new)
            .tier(GTValues.ZPM)
            .rotationState(RotationState.ALL)
            .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
            .langValue("Quantum Uplink ME Connector Hatch")
            .tooltips(Component.translatable("mm.uplink.hatch.tooltip"))
            .register();

        UPLINK = ModernManipulator.REGISTRATE
            .multiblock("mm_uplink", MMUplinkMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .appearanceBlock(GTBlocks.HIGH_POWER_CASING)
            .langValue("Matter Manipulator Quantum Uplink")
            .tooltips(
                Component.translatable("mm.uplink.tooltip.0"),
                Component.translatable("mm.uplink.tooltip.1"),
                Component.translatable("mm.uplink.tooltip.2"),
                Component.translatable("mm.uplink.tooltip.3"),
                Component.translatable("mm.uplink.tooltip.4"),
                Component.translatable("mm.uplink.tooltip.5", String.format("%,d", MMUplinkMachine.BASE_PLASMA_EU_COST)),
                Component.translatable("mm.uplink.tooltip.6"))
            .pattern(definition -> FactoryBlockPattern.start()
                .aisle("         ", "         ", "         ", "         ", "  AASAA  ", "         ", "         ", "         ", "         ")
                .aisle("         ", "         ", "  A   A  ", " AA   AA ", " AA   AA ", " AA   AA ", "  A   A  ", "         ", "         ")
                .aisle("         ", "  A   A  ", " ACCCCCA ", " AD   DA ", "A D   D A", " AD   DA ", " ACCCCCA ", "  A   A  ", "         ")
                .aisle("         ", " AA   AA ", " AD   DA ", "A       A", "A       A", "A       A", " AD   DA ", " AA   AA ", "         ")
                .aisle("  A   A  ", " AA   AA ", "A D   D A", "A       A", "ABBE EBBA", "A       A", "A D   D A", " AA   AA ", "  A   A  ")
                .aisle("         ", " AA   AA ", " AD   DA ", "A       A", "A       A", "A       A", " AD   DA ", " AA   AA ", "         ")
                .aisle("         ", "  A   A  ", " ACCCCCA ", " AD   DA ", "A D   D A", " AD   DA ", " ACCCCCA ", "  A   A  ", "         ")
                .aisle("         ", "         ", "  A   A  ", " AA   AA ", " AA   AA ", " AA   AA ", "  A   A  ", "         ", "         ")
                .aisle("         ", "         ", "         ", "         ", "  A   A  ", "         ", "         ", "         ", "         ")
                .where('S', controller(blocks(definition.getBlock())))
                .where('A', blocks(GTBlocks.HIGH_POWER_CASING.get())
                    .or(abilities(PartAbility.INPUT_ENERGY).setMinGlobalLimited(1).setPreviewCount(1))
                    .or(abilities(PartAbility.IMPORT_FLUIDS).setMinGlobalLimited(1).setPreviewCount(1))
                    .or(abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                    .or(blocks(UPLINK_HATCH.getBlock()).setMaxGlobalLimited(1).setPreviewCount(1)))
                .where('B', frames(GTMaterials.NaquadahAlloy))
                .where('C', frames(GTMaterials.Trinium))
                .where('D', blocks(GTBlocks.FUSION_COIL.get()))
                .where('E', blocks(GTBlocks.FUSION_CASING_MK2.get()))
                .where(' ', any())
                .build())
            .workableCasingModel(GTCEu.id("block/casings/hpca/high_power_casing"), ModernManipulator.id("block/multiblock/mm_uplink"))
            .register();
    }
}
