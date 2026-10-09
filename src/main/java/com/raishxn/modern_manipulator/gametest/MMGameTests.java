package com.raishxn.modern_manipulator.gametest;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.BlockStateTransformer;
import com.raishxn.modern_manipulator.common.building.IBuildable;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.building.PendingBuild;
import com.raishxn.modern_manipulator.common.building.PendingMove;
import com.raishxn.modern_manipulator.common.data.WeightedSpecList;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.items.MMUpgrades;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.BlockRemoveMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.Shape;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import org.joml.Vector3i;

import java.util.List;

/**
 * Game tests for the manipulator's core behaviour.
 * Run them with {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(ModernManipulator.MOD_ID)
@PrefixGameTestTemplate(false)
public class MMGameTests {

    private static final String TEMPLATE = "empty16";

    // #region Helpers

    private static ServerPlayer player(GameTestHelper helper, boolean creative) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);

        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        player.getInventory().clearContent();

        return player;
    }

    private static Location loc(GameTestHelper helper, int x, int y, int z) {
        return new Location(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
    }

    private static ItemStack manipulator(ItemMatterManipulator.ManipulatorTier tier) {
        ItemMatterManipulator item = (ItemMatterManipulator) switch (tier) {
            case Tier0 -> MMItems.MK0.get();
            case Tier1 -> MMItems.MK1.get();
            case Tier2 -> MMItems.MK2.get();
            case Tier3 -> MMItems.MK3.get();
        };

        return item.createChargedStack();
    }

    private static MMState state(ItemStack stack) {
        return ItemMatterManipulator.getState(stack);
    }

    /** Runs the build until it finishes (or 200 iterations pass). */
    private static void build(IBuildable buildable, ItemStack stack, Player player) {
        for (int i = 0; i < 200; i++) {
            buildable.tryPlaceBlocks(stack, player);
        }

        buildable.onStopped();
    }

    private static void runBuild(GameTestHelper helper, ItemStack stack, MMState state, Player player) {
        ItemMatterManipulator manipulator = (ItemMatterManipulator) stack.getItem();

        IBuildable buildable;

        if (state.config.placeMode == PlaceMode.MOVING) {
            buildable = new PendingMove(player, state, manipulator.tier);
        } else {
            List<PendingBlock> blocks = state.getPendingBlocks(manipulator.tier, helper.getLevel());
            blocks.sort(PendingBlock.getComparator());
            buildable = new PendingBuild(player, state, manipulator.tier, blocks);
        }

        build(buildable, stack, player);
    }

    private static WeightedSpecList list(BlockState state) {
        return new WeightedSpecList(new BlockSpec().setObject(state));
    }

    private static void fill(GameTestHelper helper, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static void clear(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 15, 15, 15, Blocks.AIR.defaultBlockState());
    }

    // #endregion

    @GameTest(template = TEMPLATE)
    public static void stateRoundTrip(GameTestHelper helper) {
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        state.config.placeMode = PlaceMode.COPYING;
        state.config.shape = Shape.CYLINDER;
        state.config.coordA = loc(helper, 1, 2, 3);
        state.config.coordB = loc(helper, 4, 5, 6);
        state.config.coordC = loc(helper, 7, 8, 9);
        state.config.corners = list(Blocks.STONE.defaultBlockState());
        state.config.arraySpan = new Vector3i(1, 2, 3);
        state.getTransform().rotate(Direction.UP, 1);
        state.getTransform().flipX = true;
        state.installUpgrade(MMUpgrades.PowerEff);

        ItemMatterManipulator.setState(stack, state);

        MMState loaded = state(stack);

        StringBuilder diff = new StringBuilder();
        for (var field : state.config.getClass().getFields()) {
            try {
                if (!java.util.Objects.equals(field.get(state.config), field.get(loaded.config))) diff.append(field.getName()).append(' ');
            } catch (IllegalAccessException ignored) {}
        }

        helper.assertTrue(loaded.config.equals(state.config), "config should survive a save/load round trip: " + diff);
        helper.assertTrue(loaded.hasUpgrade(MMUpgrades.PowerEff), "upgrades should survive a save/load round trip");
        helper.assertTrue(((ItemMatterManipulator) stack.getItem()).getCharge(stack) > 0, "charged stack should have charge");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void transformRotation(GameTestHelper helper) {
        Transform transform = new Transform();

        helper.assertTrue(transform.isIdentity(), "new transform should be the identity");
        helper.assertTrue(transform.apply(Direction.NORTH) == Direction.NORTH, "identity shouldn't change directions");

        // four quarter turns are the identity
        for (int i = 0; i < 4; i++) transform.rotate(Direction.UP, 1);

        helper.assertTrue(transform.apply(Direction.NORTH) == Direction.NORTH, "four rotations should be the identity");

        transform = new Transform();
        transform.rotate(Direction.UP, 1);

        Direction rotated = transform.apply(Direction.NORTH);

        helper.assertTrue(rotated.getAxis() == Direction.Axis.X, "a Y rotation should map north onto the X axis");
        helper.assertTrue(transform.apply(Direction.UP) == Direction.UP, "a Y rotation shouldn't change up");

        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        BlockState rotatedStairs = BlockStateTransformer.transform(stairs, transform);

        helper.assertTrue(rotatedStairs.getValue(StairBlock.FACING) == rotated, "stairs should be rotated like the transform");

        Transform flip = new Transform();
        flip.flipY = true;

        BlockState flipped = BlockStateTransformer.transform(stairs, flip);

        helper.assertTrue(flipped.getValue(StairBlock.HALF) != stairs.getValue(StairBlock.HALF), "a Y flip should flip stairs");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void geometryCube(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, true);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        state.config.placeMode = PlaceMode.GEOMETRY;
        state.config.shape = Shape.CUBE;
        state.config.coordA = loc(helper, 2, 2, 2);
        state.config.coordB = loc(helper, 4, 4, 4);
        state.config.corners = list(Blocks.GOLD_BLOCK.defaultBlockState());
        state.config.edges = list(Blocks.IRON_BLOCK.defaultBlockState());
        state.config.faces = list(Blocks.STONE.defaultBlockState());
        state.config.volumes = list(Blocks.GLASS.defaultBlockState());

        List<PendingBlock> blocks = state.getPendingBlocks(ItemMatterManipulator.ManipulatorTier.Tier3, helper.getLevel());

        helper.assertTrue(blocks.size() == 27, "a 3x3x3 cube should have 27 blocks, got " + blocks.size());

        runBuild(helper, stack, state, player);

        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, 2, 2));
        helper.assertBlockPresent(Blocks.IRON_BLOCK, new BlockPos(3, 2, 2));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 3, 2));
        helper.assertBlockPresent(Blocks.GLASS, new BlockPos(3, 3, 3));
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(4, 4, 4));

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void geometryLineSurvival(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, false);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        long chargeBefore = ((ItemMatterManipulator) stack.getItem()).getCharge(stack);

        state.config.placeMode = PlaceMode.GEOMETRY;
        state.config.shape = Shape.LINE;
        state.config.coordA = loc(helper, 1, 2, 5);
        state.config.coordB = loc(helper, 10, 2, 5);
        state.config.edges = list(Blocks.COBBLESTONE.defaultBlockState());

        // only give the player 5 cobblestone
        player.getInventory().add(new ItemStack(Items.COBBLESTONE, 5));

        runBuild(helper, stack, state, player);

        int placed = 0;
        for (int x = 1; x <= 10; x++) {
            if (helper.getBlockState(new BlockPos(x, 2, 5)).is(Blocks.COBBLESTONE)) placed++;
        }

        helper.assertTrue(placed == 5, "survival should only place as many blocks as the player has, placed " + placed);
        helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 0, "the cobblestone should have been consumed");
        helper.assertTrue(((ItemMatterManipulator) stack.getItem()).getCharge(stack) < chargeBefore, "building should use EU");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void copyWithRotation(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, true);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        // source: an L shape with a stair
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.DIRT);
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));

        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 2, 2, 2);
        state.config.coordC = loc(helper, 8, 2, 8);
        state.getTransform().rotate(Direction.UP, 1);

        Transform t = state.getTransform();

        runBuild(helper, stack, state, player);

        Vector3i dirtOffset = t.apply(new Vector3i(1, 0, 0));
        Vector3i stairOffset = t.apply(new Vector3i(0, 0, 1));

        helper.assertBlockPresent(Blocks.STONE, new BlockPos(8, 2, 8));
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(8 + dirtOffset.x, 2 + dirtOffset.y, 8 + dirtOffset.z));

        BlockState stair = helper.getBlockState(new BlockPos(8 + stairOffset.x, 2 + stairOffset.y, 8 + stairOffset.z));

        helper.assertTrue(stair.is(Blocks.OAK_STAIRS), "the stairs should be copied");
        helper.assertTrue(stair.getValue(StairBlock.FACING) == t.apply(Direction.NORTH), "the stairs should be rotated");

        // the source should be untouched
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 1));

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void copyArray(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, true);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        helper.setBlock(new BlockPos(1, 2, 1), Blocks.EMERALD_BLOCK);

        state.config.placeMode = PlaceMode.COPYING;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 1, 2, 1);
        state.config.coordC = loc(helper, 4, 2, 4);
        state.config.arraySpan = new Vector3i(3, 0, 0);

        runBuild(helper, stack, state, player);

        for (int i = 0; i <= 3; i++) {
            helper.assertBlockPresent(Blocks.EMERALD_BLOCK, new BlockPos(4 + i, 2, 4));
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void moveRegion(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, true);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        helper.setBlock(new BlockPos(1, 2, 1), Blocks.DIAMOND_BLOCK);
        helper.setBlock(new BlockPos(2, 2, 1), Blocks.CHEST);

        if (helper.getBlockEntity(new BlockPos(2, 2, 1)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.APPLE, 7));
        }

        state.config.placeMode = PlaceMode.MOVING;
        state.config.removeMode = BlockRemoveMode.ALL;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 2, 2, 1);
        state.config.coordC = loc(helper, 1, 2, 6);

        runBuild(helper, stack, state, player);

        helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 2, 1));
        helper.assertBlockPresent(Blocks.DIAMOND_BLOCK, new BlockPos(1, 2, 6));
        helper.assertBlockPresent(Blocks.CHEST, new BlockPos(2, 2, 6));

        if (helper.getBlockEntity(new BlockPos(2, 2, 6)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            helper.assertTrue(chest.getItem(0).is(Items.APPLE) && chest.getItem(0).getCount() == 7, "the chest contents should move");
        } else {
            helper.fail("the moved chest should have a block entity");
        }

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void exchange(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, true);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        fill(helper, 1, 2, 1, 3, 2, 3, Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.GOLD_BLOCK);

        state.config.placeMode = PlaceMode.EXCHANGING;
        state.config.removeMode = BlockRemoveMode.ALL;
        state.config.coordA = loc(helper, 1, 2, 1);
        state.config.coordB = loc(helper, 3, 2, 3);
        state.config.replaceWhitelist = list(Blocks.STONE.defaultBlockState());
        state.config.replaceWith = list(Blocks.OAK_PLANKS.defaultBlockState());

        runBuild(helper, stack, state, player);

        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(1, 2, 1));
        helper.assertBlockPresent(Blocks.OAK_PLANKS, new BlockPos(3, 2, 3));
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(2, 2, 2));

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void removeModeNone(GameTestHelper helper) {
        clear(helper);

        ServerPlayer player = player(helper, true);
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        helper.setBlock(new BlockPos(2, 2, 2), Blocks.DIRT);

        state.config.placeMode = PlaceMode.GEOMETRY;
        state.config.shape = Shape.LINE;
        state.config.removeMode = BlockRemoveMode.NONE;
        state.config.coordA = loc(helper, 1, 2, 2);
        state.config.coordB = loc(helper, 3, 2, 2);
        state.config.edges = list(Blocks.STONE.defaultBlockState());

        runBuild(helper, stack, state, player);

        helper.assertBlockPresent(Blocks.STONE, new BlockPos(1, 2, 2));
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(2, 2, 2));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 2));

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void geometrySphereAndCylinder(GameTestHelper helper) {
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier3);
        MMState state = state(stack);

        state.config.placeMode = PlaceMode.GEOMETRY;
        state.config.shape = Shape.SPHERE;
        state.config.coordA = loc(helper, 0, 2, 0);
        state.config.coordB = loc(helper, 6, 8, 6);
        state.config.volumes = list(Blocks.STONE.defaultBlockState());
        state.config.faces = list(Blocks.GLASS.defaultBlockState());

        int sphere = state.getPendingBlocks(ItemMatterManipulator.ManipulatorTier.Tier3, helper.getLevel()).size();

        helper.assertTrue(sphere > 100 && sphere < 343, "a 7^3 sphere should have between 100 and 343 blocks, got " + sphere);

        state.config.shape = Shape.CYLINDER;
        state.config.coordA = loc(helper, 0, 2, 0);
        state.config.coordB = loc(helper, 6, 2, 6);
        state.config.coordC = loc(helper, 0, 6, 0);
        state.config.edges = list(Blocks.STONE.defaultBlockState());

        int cylinder = state.getPendingBlocks(ItemMatterManipulator.ManipulatorTier.Tier3, helper.getLevel()).size();

        helper.assertTrue(cylinder > 0, "the cylinder should have blocks");

        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void upgradeCapabilities(GameTestHelper helper) {
        ItemStack stack = manipulator(ItemMatterManipulator.ManipulatorTier.Tier0);
        MMState state = state(stack);

        helper.assertFalse(state.hasCap(ItemMatterManipulator.ALLOW_REMOVING), "the prototype can't remove blocks by default");
        helper.assertTrue(state.couldAcceptUpgrade(ItemMatterManipulator.ManipulatorTier.Tier0, MMUpgrades.Mining), "the prototype accepts the mining upgrade");
        helper.assertFalse(state.couldAcceptUpgrade(ItemMatterManipulator.ManipulatorTier.Tier0, MMUpgrades.PowerP2P), "the prototype doesn't accept the p2p upgrade");

        state.installUpgrade(MMUpgrades.Mining);

        helper.assertTrue(state.hasCap(ItemMatterManipulator.ALLOW_REMOVING), "the mining upgrade allows removing");

        helper.succeed();
    }
}
