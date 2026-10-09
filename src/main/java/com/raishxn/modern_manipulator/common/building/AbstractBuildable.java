package com.raishxn.modern_manipulator.common.building;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.server.ServerLifecycleHooks;

import com.raishxn.modern_manipulator.common.compat.BlockRemovers;
import com.raishxn.modern_manipulator.common.items.MMUpgrades;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.utils.MMUtils;
import it.unimi.dsi.fastutil.Pair;

import java.util.HashMap;
import java.util.List;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendWarningToPlayer;

/**
 * Handles all generic manipulator building logic.
 */
public abstract class AbstractBuildable extends MMInventory implements IBuildable {

    private static final double[] SQUARE_ROOTS = new double[1000];

    static {
        for (int i = 0; i < SQUARE_ROOTS.length; i++) {
            SQUARE_ROOTS[i] = 1 + Math.sqrt(i);
        }
    }

    public AbstractBuildable(Player player, MMState state, ManipulatorTier tier) {
        super(player, state, tier);
    }

    protected static final double EU_PER_BLOCK = 128.0, TE_PENALTY = 16.0, EU_DISTANCE_EXP = 1.25;

    /** Sub-EU usage that hasn't been drained yet, so that fractional costs aren't lost. */
    private double euDebt = 0;

    public boolean tryConsumePower(ItemStack stack, Level world, int x, int y, int z, ImmutableBlockSpec spec) {
        BlockPos pos = new BlockPos(x, y, z);

        int hardness = (int) spec.getBlockState().getDestroySpeed(world, pos);

        if (hardness < 0) hardness = 0;
        if (hardness > 999) hardness = 999;

        double euUsage = EU_PER_BLOCK * SQUARE_ROOTS[hardness];

        if (spec.getBlockState().hasBlockEntity()) {
            euUsage *= TE_PENALTY;
        }

        return tryConsumePower(stack, x, y, z, euUsage);
    }

    public boolean tryConsumePower(ItemStack stack, double x, double y, double z, double euUsage) {
        if (player.isCreative()) return true;

        euUsage *= Math.pow(Math.sqrt(player.distanceToSqr(x, y, z)), EU_DISTANCE_EXP);

        if (state.hasUpgrade(MMUpgrades.PowerEff)) {
            euUsage *= 0.5;
        }

        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);

        if (electricItem == null) return false;

        euDebt += euUsage;

        long toDrain = (long) Math.floor(euDebt);

        if (toDrain <= 0) return true;

        long drained = electricItem.discharge(toDrain, Integer.MAX_VALUE, true, false, true);

        if (drained < toDrain) return false;

        electricItem.discharge(toDrain, Integer.MAX_VALUE, true, false, false);
        euDebt -= toDrain;

        return true;
    }

    public void refillPower(ItemStack stack) {
        ItemMatterManipulator manipulator = (ItemMatterManipulator) stack.getItem();

        manipulator.refillPower(stack, state);
    }

    /**
     * Removes a block and stores its items in this object. Items & fluids must delivered by calling
     * {@link #actuallyGivePlayerStuff()} or they will be deleted.
     */
    protected void removeBlock(Level world, int x, int y, int z, ImmutableBlockSpec existing) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState blockState = world.getBlockState(pos);

        boolean voidDrops = !existing.shouldDropItem();

        if (!voidDrops) {
            if (blockState.is(Tags.Blocks.ORES) || BlockRemovers.isOre(blockState)) {
                voidDrops = true;
            }
        }

        if (voidDrops) {
            try {
                BlockCaptureDrops.captureDrops(world);
                world.removeBlockEntity(pos);
                world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } finally {
                BlockCaptureDrops.stopCapturingDrops(world);
            }

            return;
        }

        BlockEntity te = world.getBlockEntity(pos);

        if (te != null) {
            BlockRemovers.reset(this, blockState, te);
            emptyTileInventory(te);
            emptyTank(te);
        }

        FluidState fluidState = blockState.getFluidState();

        if (blockState.getBlock() instanceof LiquidBlock && fluidState.isSource()) {
            givePlayerFluids(new FluidStack(fluidState.getType(), 1000));
        } else if (blockState.getBlock() instanceof BucketPickup && !fluidState.isEmpty() && fluidState.isSource() &&
                !(blockState.getBlock() instanceof LiquidBlock)) {
                    // waterlogged blocks: drop the block and the fluid
                    givePlayerFluids(new FluidStack(fluidState.getType(), 1000));
                    givePlayerDrops(world, pos, blockState, te);
                } else {
                    givePlayerDrops(world, pos, blockState, te);
                }

        try {
            BlockCaptureDrops.captureDrops(world);

            // the block entity is kept so that blocks can drop their remaining contents (they're captured)
            world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } finally {
            givePlayerItems(BlockCaptureDrops.stopCapturingDrops(world).toArray(new ItemStack[0]));
        }
    }

    private void givePlayerDrops(Level world, BlockPos pos, BlockState blockState, BlockEntity te) {
        if (!(world instanceof ServerLevel serverLevel)) return;

        List<ItemStack> items = Block.getDrops(blockState, serverLevel, pos, te, player, ItemStack.EMPTY);

        givePlayerItems(items.toArray(new ItemStack[0]));
    }

    protected void emptyTileInventory(BlockEntity te) {
        InventoryAdapter adapter = InventoryAdapter.findAdapter(te);

        if (adapter == null) return;

        MMUtils.emptyInventory(this, adapter.getHandler(te));
    }

    protected void emptyTank(BlockEntity te) {
        IFluidHandler handler = te.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);

        if (handler == null) return;

        if (!BlockRemovers.shouldEmptyTanks(te)) return;

        int i = 0;
        FluidStack fluid;
        while (!(fluid = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE)).isEmpty()) {
            givePlayerFluids(fluid);

            if (i++ > 1000) break;
        }
    }

    private static class SoundInfo {

        private int eventCount;
        private double sumX, sumY, sumZ;
    }

    private final HashMap<Pair<SoundEvent, Level>, SoundInfo> pendingSounds = new HashMap<>();

    private boolean printedProtectedBlockWarning = false;

    /**
     * Queues a sound to be played at a specific spot.
     * This doesn't actually play anything, {@link #playSounds()} must be called to play all queued sounds.
     * This mechanism finds the centre point for all played sounds of the same type and makes a single sound event so
     * that several aren't played in the same tick.
     */
    protected void playSound(Level world, int x, int y, int z, SoundEvent sound) {
        Pair<SoundEvent, Level> pair = Pair.of(sound, world);

        SoundInfo info = pendingSounds.computeIfAbsent(pair, ignored -> new SoundInfo());

        info.eventCount++;
        info.sumX += x;
        info.sumY += y;
        info.sumZ += z;
    }

    protected void playSounds() {
        pendingSounds.forEach((pair, info) -> {
            double avgX = info.sumX / info.eventCount + 0.5;
            double avgY = info.sumY / info.eventCount + 0.5;
            double avgZ = info.sumZ / info.eventCount + 0.5;

            float distance = (float) Math.sqrt(player.distanceToSqr(avgX, avgY, avgZ));

            pair.right().playSound(null, avgX, avgY, avgZ, pair.left(), SoundSource.BLOCKS, (distance / 16f) + 1, 1f);
        });
        pendingSounds.clear();
    }

    /**
     * Checks if a block can be edited.
     */
    protected boolean isEditable(Level world, int x, int y, int z, boolean isPlacement) {
        BlockPos pos = new BlockPos(x, y, z);

        boolean isBlocked;

        if (isPlacement) {
            isBlocked = ForgeEventFactory.onBlockPlace(player, BlockSnapshot.create(world.dimension(), world, pos),
                    Direction.UP);
        } else {
            isBlocked = MinecraftForge.EVENT_BUS
                    .post(new BlockEvent.BreakEvent(world, pos, world.getBlockState(pos), player));
        }

        var server = ServerLifecycleHooks.getCurrentServer();

        boolean spawnProtected = server != null && world instanceof ServerLevel serverLevel &&
                server.isUnderSpawnProtection(serverLevel, pos, player);

        // if this block is protected, ignore it completely and print a warning
        if (isBlocked || !world.mayInteract(player, pos) || spawnProtected ||
                !world.getWorldBorder().isWithinBounds(pos)) {
            if (!printedProtectedBlockWarning) {
                sendWarningToPlayer(player, "mm.info.warning.protected_area");
                printedProtectedBlockWarning = true;
            }

            return false;
        } else {
            return true;
        }
    }

    public Player getPlayer() {
        return player;
    }

    public ServerPlayer getServerPlayer() {
        return (ServerPlayer) player;
    }

    protected static boolean isBlockedByTag(BlockState state) {
        return state.is(BlockTags.FEATURES_CANNOT_REPLACE);
    }
}
