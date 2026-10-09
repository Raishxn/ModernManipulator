package com.raishxn.modern_manipulator.common.building;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendInfoToPlayer;

import com.raishxn.modern_manipulator.common.building.movers.BlockMover;
import com.raishxn.modern_manipulator.common.building.movers.BlockMovers;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMConfig;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import it.unimi.dsi.fastutil.Pair;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all moving logic.
 */
public class PendingMove extends AbstractBuildable {

    private List<Pair<Location, Location>> moves = null;

    private int moveOffsetX, moveOffsetY, moveOffsetZ;
    private int srcMinX, srcMinY, srcMinZ, srcMaxX, srcMaxY, srcMaxZ;

    /** Positions that were changed, so that their neighbours can be updated once the move finishes. */
    private final List<BlockPos> changed = new ArrayList<>();

    /** State that block movers carry from remove() to place(), keyed by the removed block's state object. */
    private final Map<Object, Object> moverState = new IdentityHashMap<>();

    public PendingMove(Player player, MMState state, ManipulatorTier tier) {
        super(player, state, tier);
    }

    public int getMoveOffsetX() {
        return moveOffsetX;
    }

    public int getMoveOffsetY() {
        return moveOffsetY;
    }

    public int getMoveOffsetZ() {
        return moveOffsetZ;
    }

    public boolean isInSourceRegion(int x, int y, int z) {
        return x >= srcMinX && x <= srcMaxX && y >= srcMinY && y <= srcMaxY && z >= srcMinZ && z <= srcMaxZ;
    }

    public Map<Object, Object> getMoverState() {
        return moverState;
    }

    @Override
    public void tryPlaceBlocks(ItemStack stack, Player player) {
        resetWarnings();
        refillPower(stack);

        if (moves == null) {
            initMoves();
        }

        Level world = player.level();

        BlockSpec source = new BlockSpec();
        BlockSpec target = new BlockSpec();

        int ops = 0;
        var iter = moves.listIterator(moves.size());

        ArrayList<Pair<Location, Location>> shuffled = new ArrayList<>();

        int placeSpeed = tier.getPlaceSpeed();

        // try to move `placeSpeed` blocks from here to there
        while (ops < placeSpeed && iter.hasPrevious()) {
            Pair<Location, Location> move = iter.previous();

            Location s = move.left();
            Location d = move.right();

            BlockPos sPos = s.toPos();
            BlockPos dPos = d.toPos();

            BlockSpec.fromBlock(source, world, sPos);

            // if either block is protected, ignore them completely and print a warning
            if (!isEditable(world, s.x, s.y, s.z, false) || !isEditable(world, d.x, d.y, d.z, true)) {
                sendErrorToPlayer(player, "mm.info.error.could_not_move_protected_block.new", s.x, s.y, s.z, source.getChatComponent());
                iter.remove();
                continue;
            }

            BlockState sourceState = world.getBlockState(sPos);

            if (sourceState.isAir()) {
                iter.remove();
                continue;
            }

            if (sourceState.getDestroySpeed(world, sPos) < 0) {
                sendErrorToPlayer(
                    player,
                    "mm.info.error.could_not_move_invulnerable_block.new",
                    s.x,
                    s.y,
                    s.z,
                    source.getChatComponent());
                iter.remove();
                continue;
            }

            BlockSpec.fromBlock(target, world, dPos);
            BlockState targetState = world.getBlockState(dPos);

            // check if we can remove the existing target block
            boolean canPlace = switch (state.config.removeMode) {
                case NONE -> targetState.isAir();
                case REPLACEABLE -> targetState.canBeReplaced();
                case ALL -> true;
            };

            canPlace &= targetState.getDestroySpeed(world, dPos) >= 0;

            if (!canPlace) {
                sendErrorToPlayer(player, "mm.info.error.could_not_move_blocked_block.new", d.x, d.y, d.z, source.getChatComponent());
                iter.remove();
                continue;
            }

            // remove the existing block if needed
            if (!targetState.isAir()) {
                if (!tryConsumePower(stack, world, d.x, d.y, d.z, target)) {
                    sendErrorToPlayer(player, "mm.info.error.out_of_eu");
                    break;
                }

                removeBlock(world, d.x, d.y, d.z, target);
            }

            // if we can't move the source block then skip it for now
            if (!sourceState.canSurvive(world, dPos)) {
                shuffled.add(move);
                iter.remove();
                continue;
            }

            if (!tryConsumePower(stack, world, s.x, s.y, s.z, source)) {
                sendErrorToPlayer(player, "mm.info.error.out_of_eu");
                break;
            }

            // try to move the source block into the (now empty) target block
            if (!moveBlock(this, world, s, source, d, target)) {
                sendErrorToPlayer(player, "mm.info.error.could_not_move_block.new", s.x, s.y, s.z, source.getChatComponent());
            }

            changed.add(sPos);
            changed.add(dPos);

            playSound(world, s.x, s.y, s.z, SoundEvents.ENDERMAN_TELEPORT);
            playSound(world, d.x, d.y, d.z, SoundEvents.ENDERMAN_TELEPORT);

            iter.remove();
            ops++;
        }

        moves.addAll(shuffled);

        updateChangedBlocks(world);

        playSounds();
        actuallyGivePlayerStuff();

        if (ops > 0) {
            sendInfoToPlayer(player, "mm.info.process_move", ops, moves.size());
        } else {
            sendInfoToPlayer(player, "mm.info.finished_move");
        }
    }

    /**
     * Notifies the neighbours of every moved block once the blocks have been moved.
     */
    private void updateChangedBlocks(Level world) {
        for (BlockPos pos : changed) {
            BlockState state = world.getBlockState(pos);

            state.updateNeighbourShapes(world, pos, Block.UPDATE_ALL);
            world.updateNeighborsAt(pos, state.getBlock());
        }

        changed.clear();
    }

    @Override
    public void onStopped() {

    }

    private void initMoves() {
        moves = new ArrayList<>();

        Location startA = state.config.coordA;
        Location startB = state.config.coordB;
        Location dest = state.config.coordC;

        if (!Location.areCompatible(startA, startB, dest)) return;

        MMConfig.VoxelAABB cut = new MMConfig.VoxelAABB(startA.toVec(), startB.toVec());
        MMConfig.VoxelAABB paste = cut.clone().moveOrigin(dest.toVec());

        if (cut.toBoundingBox().intersects(paste.toBoundingBox())) {
            MMUtils.sendErrorToPlayer(player, "mm.info.error.move_overlapping");
            return;
        }

        int x1 = startA.x;
        int y1 = startA.y;
        int z1 = startA.z;
        int x2 = startB.x;
        int y2 = startB.y;
        int z2 = startB.z;

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);

        String worldId = startA.worldId;

        moveOffsetX = dest.x - startA.x;
        moveOffsetY = dest.y - startA.y;
        moveOffsetZ = dest.z - startA.z;
        srcMinX = minX;
        srcMinY = minY;
        srcMinZ = minZ;
        srcMaxX = maxX;
        srcMaxY = maxY;
        srcMaxZ = maxZ;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int dX = x - x1;
                    int dY = y - y1;
                    int dZ = z - z1;

                    moves.add(Pair.of(new Location(worldId, x, y, z), new Location(worldId, dest.x + dX, dest.y + dY, dest.z + dZ)));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean moveBlock(PendingMove pendingMove, Level world, Location s, BlockSpec spec1, Location d, BlockSpec spec2) {
        Level worldS = s.isInWorld(world) ? world : s.getWorld();
        Level worldD = d.isInWorld(world) ? world : d.getWorld();

        if (worldS == null || worldD == null) return false;

        BlockPos sPos = s.toPos();
        BlockPos dPos = d.toPos();

        if (worldS.getBlockState(sPos).isAir() && worldD.getBlockState(dPos).isAir()) return false;

        BlockMover<Object> source = (BlockMover<Object>) BlockMovers.getBlockMover(worldS, sPos);
        BlockMover<Object> dest = (BlockMover<Object>) BlockMovers.getBlockMover(worldD, dPos);

        try {
            BlockCaptureDrops.captureDrops(worldS);
            if (worldS != worldD) BlockCaptureDrops.captureDrops(worldD);

            Object sourceState = source.remove(pendingMove, worldS, sPos);
            Object destState = dest.remove(pendingMove, worldD, dPos);

            source.place(pendingMove, worldD, dPos, sourceState);
            dest.place(pendingMove, worldS, sPos, destState);
        } finally {
            // delete any items that were dropped
            BlockCaptureDrops.stopCapturingDrops(worldS);
            if (worldS != worldD) BlockCaptureDrops.stopCapturingDrops(worldD);
        }

        return true;
    }
}
