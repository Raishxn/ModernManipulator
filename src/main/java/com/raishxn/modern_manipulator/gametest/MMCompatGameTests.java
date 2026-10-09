package com.raishxn.modern_manipulator.gametest;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import appeng.api.parts.IPartHost;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.building.PendingBuild;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;

import java.util.List;

/**
 * Game tests for the GregTech and AE2 integrations.
 */
@GameTestHolder(ModernManipulator.MOD_ID)
@PrefixGameTestTemplate(false)
public class MMCompatGameTests {

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setGameMode(GameType.CREATIVE);

        return player;
    }

    private static Location loc(GameTestHelper helper, int x, int y, int z) {
        return new Location(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static void build(GameTestHelper helper, ItemStack stack, MMState state, ServerPlayer player) {
        List<PendingBlock> blocks = state.getPendingBlocks(ManipulatorTier.Tier3, helper.getLevel());
        blocks.sort(PendingBlock.getComparator());

        PendingBuild build = new PendingBuild(player, state, ManipulatorTier.Tier3, blocks);

        for (int i = 0; i < 50; i++) build.tryPlaceBlocks(stack, player);

        build.onStopped();
    }

    private static void clear(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) for (int y = 1; y < 16; y++) for (int z = 0; z < 16; z++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    @GameTest(template = "empty16")
    public static void copyGTMachineWithCover(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        BlockPos src = new BlockPos(1, 2, 1);
        helper.setBlock(src, GTMachines.ELECTRIC_FURNACE[GTValues.LV].getBlock().defaultBlockState());

        if (!(helper.getBlockEntity(src) instanceof IMachineBlockEntity mbe)) {
            helper.fail("the furnace should be a machine");
            return;
        }

        MetaMachine machine = mbe.getMetaMachine();

        machine.getCoverContainer()
                .placeCoverOnSide(Direction.UP, GTItems.CONVEYOR_MODULE_LV.asStack(), GTCovers.CONVEYORS[0], player);
        machine.setPaintingColor(0xFF0000);

        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 1, 2, 1);
        state.config.coordC = loc(helper, 6, 2, 6);

        build(helper, stack, state, player);

        BlockPos dest = new BlockPos(6, 2, 6);

        if (!(helper.getBlockEntity(dest) instanceof IMachineBlockEntity copy)) {
            helper.fail("the furnace should be copied");
            return;
        }

        helper.assertTrue(copy.getMetaMachine().getCoverContainer().getCoverAtSide(Direction.UP) != null,
                "the cover should be copied");
        helper.assertTrue(copy.getMetaMachine().getPaintingColor() == 0xFF0000, "the colour should be copied");

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void exchangeKeepsFacing(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        var lv = GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].getBlock().defaultBlockState();
        var hv = GTMachines.ENERGY_INPUT_HATCH[GTValues.HV].getBlock().defaultBlockState();
        var facing = (net.minecraft.world.level.block.state.properties.DirectionProperty) lv.getBlock()
                .getStateDefinition().getProperty("facing");
        Direction original = lv.getValue(facing) == Direction.EAST ? Direction.WEST : Direction.EAST;

        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, lv.setValue(facing, original));

        state.config.placeMode = PlaceMode.EXCHANGING;
        state.config.coordA = loc(helper, 2, 2, 2);
        state.config.coordB = loc(helper, 2, 2, 2);
        state.config.replaceWhitelist = new com.raishxn.modern_manipulator.common.data.WeightedSpecList(
                new BlockSpec().setObject(lv));
        state.config.replaceWith = new com.raishxn.modern_manipulator.common.data.WeightedSpecList(
                new BlockSpec().setObject(hv));

        build(helper, stack, state, player);

        var placed = helper.getBlockState(pos);
        helper.assertTrue(placed.is(hv.getBlock()), "the hatch should be exchanged");
        helper.assertTrue(placed.getValue(facing) == original,
                "the exchanged hatch should keep its facing, got " + placed.getValue(facing));

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void copyControllerSettings(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        BlockPos src = new BlockPos(1, 2, 1);
        helper.setBlock(src, com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.ELECTRIC_BLAST_FURNACE
                .getBlock().defaultBlockState());

        if (!(helper.getBlockEntity(src) instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine controller)) {
            helper.fail("the EBF should be a workable electric multiblock");
            return;
        }

        controller.setVoidingMode(com.gregtechceu.gtceu.api.machine.feature.IVoidable.VoidingMode.VOID_ITEMS);
        controller.setBatchEnabled(true);

        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 1, 2, 1);
        state.config.coordC = loc(helper, 6, 2, 6);

        build(helper, stack, state, player);

        if (!(helper.getBlockEntity(new BlockPos(6, 2, 6)) instanceof IMachineBlockEntity copyBe &&
                copyBe.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine copy)) {
            helper.fail("the EBF controller should be copied");
            return;
        }

        helper.assertTrue(
                copy.getVoidingMode() == com.gregtechceu.gtceu.api.machine.feature.IVoidable.VoidingMode.VOID_ITEMS,
                "the voiding mode should be copied, got " + copy.getVoidingMode());
        helper.assertTrue(copy.isBatchEnabled(), "batch mode should be copied");

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void copyRotorHolderRotor(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        var holderDef = GTMachines.ROTOR_HOLDER[GTValues.HV];
        ItemStack rotor = GTItems.TURBINE_ROTOR.asStack();
        com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour.getBehaviour(rotor).setPartMaterial(rotor,
                GTMaterials.Steel);

        BlockPos src = new BlockPos(1, 2, 1);
        helper.setBlock(src, holderDef.getBlock().defaultBlockState());

        if (!(helper.getBlockEntity(src) instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine() instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine holder)) {
            helper.fail("the rotor holder should be a rotor holder machine");
            return;
        }

        holder.inventory.storage.setStackInSlot(0, rotor.copy());

        player.getInventory().add(holderDef.asStack());
        player.getInventory().add(rotor.copy());

        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 1, 2, 1);
        state.config.coordC = loc(helper, 6, 2, 6);

        build(helper, stack, state, player);

        if (!(helper.getBlockEntity(new BlockPos(6, 2, 6)) instanceof IMachineBlockEntity copyBe &&
                copyBe.getMetaMachine() instanceof com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine copy)) {
            helper.fail("the rotor holder should be copied");
            return;
        }

        helper.assertTrue(ItemStack.isSameItemSameTags(copy.inventory.storage.getStackInSlot(0), rotor),
                "the rotor should be copied");
        helper.assertTrue(player.getInventory().countItem(GTItems.TURBINE_ROTOR.get()) == 0,
                "the rotor should be taken from the player");

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void uplinkStructureForms(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);

        helper.assertTrue(GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Trinium) != null,
                "the trinium frame should exist (the uplink structure needs it)");

        BlockPos pos = new BlockPos(8, 6, 1);
        helper.setBlock(pos,
                com.raishxn.modern_manipulator.common.uplink.MMUplinkMachines.UPLINK.getBlock().defaultBlockState());

        if (!(helper.getBlockEntity(pos) instanceof IMachineBlockEntity mbe &&
                mbe.getMetaMachine() instanceof com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine controller)) {
            helper.fail("the uplink should be a multiblock controller");
            return;
        }

        controller.getPattern().autoBuild(player, controller.getMultiblockState());

        helper.assertTrue(controller.checkPattern(), "the auto built uplink structure should be valid: " +
                controller.getMultiblockState().error);

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void uplinkRecipesAreCraftable(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();

        for (String id : new String[] { "mm_uplink", "mm_uplink_me_hatch" }) {
            var recipe = recipes.byKey(new ResourceLocation(ModernManipulator.MOD_ID, id)).orElse(null);

            if (recipe == null) {
                helper.fail("the " + id + " recipe should be loaded");
                return;
            }

            for (var ingredient : recipe.getIngredients()) {
                helper.assertTrue(ingredient.isEmpty() || ingredient.getItems().length > 0,
                        "every ingredient of " + id + " should match an item");
            }

            helper.assertTrue(!recipe.getResultItem(helper.getLevel().registryAccess()).isEmpty(),
                    id + " should have a result");
        }

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void gtCableLine(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        Block cable = GTMaterialBlocks.CABLE_BLOCKS.get(TagPrefix.cableGtSingle, GTMaterials.Tin).get();

        state.config.placeMode = PlaceMode.CABLES;
        state.config.coordA = loc(helper, 1, 2, 3);
        state.config.coordB = loc(helper, 6, 2, 3);
        state.config.cables = new BlockSpec().setObject(cable.defaultBlockState());

        build(helper, stack, state, player);

        for (int x = 1; x <= 6; x++) {
            helper.assertBlockPresent(cable, new BlockPos(x, 2, 3));
        }

        if (helper.getBlockEntity(new BlockPos(3, 2, 3)) instanceof PipeBlockEntity<?, ?> pipe) {
            helper.assertTrue(PipeBlockEntity.isConnected(pipe.getConnections(), Direction.EAST) &&
                    PipeBlockEntity.isConnected(pipe.getConnections(), Direction.WEST),
                    "the middle cable should connect both ways");
        } else {
            helper.fail("the cable should have a block entity");
        }

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void aeCableLine(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        var cable = BuiltInRegistries.ITEM.get(new ResourceLocation("ae2", "fluix_glass_cable"));

        state.config.placeMode = PlaceMode.CABLES;
        state.config.coordA = loc(helper, 1, 2, 5);
        state.config.coordB = loc(helper, 1, 6, 5);
        state.config.cables = new BlockSpec().setObject(new ItemStack(cable));

        build(helper, stack, state, player);

        for (int y = 2; y <= 6; y++) {
            if (!(helper.getBlockEntity(new BlockPos(1, y, 5)) instanceof IPartHost host) ||
                    host.getPart(null) == null) {
                helper.fail("there should be an ae cable at y=" + y);
                return;
            }
        }

        helper.succeed();
    }

    @GameTest(template = "empty16")
    public static void smartCopyCribToProxy(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper);
        ItemStack stack = ((ItemMatterManipulator) MMItems.MK3.get()).createChargedStack();
        MMState state = ItemMatterManipulator.getState(stack);

        BlockPos src = new BlockPos(1, 2, 1);
        helper.setBlock(src, com.gregtechceu.gtceu.common.data.machines.GTAEMachines.ME_PATTERN_BUFFER.getBlock()
                .defaultBlockState());

        state.config.placeMode = PlaceMode.COPYING;
        state.config.replaceCribsWithProxies = true;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 1, 2, 1);
        state.config.coordC = loc(helper, 5, 2, 5);

        build(helper, stack, state, player);

        if (!(helper.getBlockEntity(new BlockPos(5, 2, 5)) instanceof IMachineBlockEntity mbe) ||
                !(mbe.getMetaMachine() instanceof com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyPartMachine proxy)) {
            helper.fail("a pattern buffer proxy should be pasted");
            return;
        }

        helper.assertTrue(proxy.getBuffer() != null && proxy.getBuffer().getPos().equals(helper.absolutePos(src)),
                "the proxy should be linked to the source buffer");

        helper.succeed();
    }
}
