package com.raishxn.modern_manipulator.common.items.manipulator;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.min;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.signum;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.BlockAnalyzer;
import com.raishxn.modern_manipulator.common.building.BlockAnalyzer.RegionAnalysis;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.ImmutableBlockSpec;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.compat.CableHandlers;
import com.raishxn.modern_manipulator.common.compat.MEConnection;
import com.raishxn.modern_manipulator.common.compat.SmartCopyHandlers;
import com.raishxn.modern_manipulator.common.data.WeightedSpecList;
import com.raishxn.modern_manipulator.common.items.MMUpgrades;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.persist.BitSetJsonAdapter;
import com.raishxn.modern_manipulator.common.persist.DirectionJsonAdapter;
import com.raishxn.modern_manipulator.common.persist.NBTJsonAdapter;
import com.raishxn.modern_manipulator.common.persist.WeightedListJsonAdapter;
import com.raishxn.modern_manipulator.common.uplink.IUplinkMulti;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * The NBT state of a manipulator.
 */
public class MMState {

    static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(CompoundTag.class, new NBTJsonAdapter())
        .registerTypeAdapter(Direction.class, new DirectionJsonAdapter())
        .registerTypeAdapter(WeightedSpecList.class, new WeightedListJsonAdapter())
        .registerTypeAdapter(BitSet.class, new BitSetJsonAdapter())
        .create();

    /** The item tag key that the state is stored in. */
    public static final String TAG_KEY = "mm";

    @SerializedName("jv")
    private int jsonVersion = LASTEST_JSON_VERSION;
    @SerializedName("dv")
    private int dataVersion = LASTEST_DATA_VERSION;

    public MMConfig config = new MMConfig();

    /** The linked ME network (the location of the wireless access point the manipulator was linked to). */
    public Location meLink;
    public Long uplinkAddress;

    public BitSet installedUpgrades = new BitSet();
    public transient int upgradeProvidedCapabilities;

    public transient ItemMatterManipulator manipulator;

    /** The live ME connection, only present on the server and when AE2 is loaded. */
    public transient MEConnection me;

    public static MMState load(@Nullable CompoundTag itemTag) {
        MMState state = null;

        if (itemTag != null && itemTag.contains(TAG_KEY)) {
            try {
                JsonObject obj = JsonParser.parseString(itemTag.getString(TAG_KEY)).getAsJsonObject();

                migrateJson(obj);

                state = GSON.fromJson(obj, MMState.class);
            } catch (Exception e) {
                ModernManipulator.LOG.error("Could not load manipulator state, it will be reset", e);
            }
        }

        if (state == null) state = new MMState();
        if (state.config == null) state.config = new MMConfig();
        if (state.installedUpgrades == null) state.installedUpgrades = new BitSet();

        state.migrate();
        state.onLoad();

        return state;
    }

    public String save() {
        return GSON.toJson(this);
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public MMState clone() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_KEY, save());

        MMState copy = load(tag);

        copy.manipulator = this.manipulator;

        return copy;
    }

    private static final int LASTEST_JSON_VERSION = 1;
    private static final int LASTEST_DATA_VERSION = 0;

    private static void migrateJson(JsonObject obj) {
        int version = obj.has("jv") ? obj.get("jv").getAsInt() : LASTEST_JSON_VERSION;

        obj.addProperty("jv", version);
    }

    private void migrate() {

    }

    private void onLoad() {
        for (MMUpgrades upgrade : getInstalledUpgrades()) {
            upgradeProvidedCapabilities |= upgrade.providesCaps;
        }
    }

    // #region ME

    /**
     * True if the ME system could be connected to.
     */
    public boolean hasMEConnection() {
        return me != null && me.isConnected();
    }

    /**
     * Tries to connect to an ME system, if possible.
     */
    public boolean connectToMESystem() {
        if (meLink == null) {
            me = null;
            return false;
        }

        me = MEConnection.connect(meLink);

        return hasMEConnection();
    }

    /**
     * Checks if the player is currently within range of an access point and the access point is online.
     */
    public boolean canInteractWithAE(Player player) {
        return me != null && me.canInteract(player);
    }

    // #endregion

    // #region Uplink

    public transient IUplinkMulti uplink;

    /**
     * Tries to connect to the uplink, if possible.
     */
    public boolean connectToUplink() {
        uplink = null;

        if (uplinkAddress != null && uplinkAddress != 0) {
            uplink = IUplinkMulti.getUplink(uplinkAddress);

            if (uplink != null) {
                if (!uplink.isActive()) {
                    uplink = null;
                }
            }
        }

        return hasUplinkConnection();
    }

    public boolean hasUplinkConnection() {
        return uplink != null;
    }

    // #endregion

    public boolean hasCap(int cap) {
        return (manipulator.tier.capabilities & cap) == cap || (upgradeProvidedCapabilities & cap) == cap;
    }

    public Transform getTransform() {
        if (config.transform == null) config.transform = new Transform();
        return config.transform;
    }

    // #region Pending blocks

    /**
     * Gets the pending blocks for this manipulator.
     * Note: moving uses a special algorithm, so its value returned here should only be used for drawing the hints.
     */
    public List<PendingBlock> getPendingBlocks(ManipulatorTier tier, Level world) {
        return switch (config.placeMode) {
            case COPYING, MOVING -> getAnalysis(world);
            case GEOMETRY -> getGeomPendingBlocks(world);
            case EXCHANGING -> getExchangeBlocks(tier, world);
            case CABLES -> getCableBlocks(world);
        };
    }

    private List<PendingBlock> getAnalysis(Level world) {
        Location coordA = config.coordA;
        Location coordB = config.coordB;
        Location coordC = config.coordC;

        if (!Location.areCompatible(coordA, coordB, coordC) || !coordA.isInWorld(world)) return new ArrayList<>();

        // MOVING's result is only used visually since it has a special build algorithm
        RegionAnalysis analysis = BlockAnalyzer.analyzeRegion(world, coordA, coordB, config.placeMode == PlaceMode.COPYING);

        if (analysis == null) return new ArrayList<>();

        if (config.placeMode == PlaceMode.COPYING && (config.replaceCribsWithProxies || config.replaceInterfacesWithP2P)) {
            SmartCopyHandlers.apply(this, world, analysis.blocks, coordA);
        }

        if (config.placeMode == PlaceMode.COPYING) {
            Transform t = getTransform();

            t.cacheRotation();

            // apply rotation
            for (PendingBlock block : analysis.blocks) {
                Vector3i v = t.apply(block.toVec());

                block.x = v.x;
                block.y = v.y;
                block.z = v.z;

                block.transform(t);
            }

            // offset to the correct location (needs to be after rotating)
            for (PendingBlock block : analysis.blocks) {
                block.x += coordC.x;
                block.y += coordC.y;
                block.z += coordC.z;
            }

            // copy the blocks (arraying)
            if (config.arraySpan != null) {
                List<PendingBlock> base = new ArrayList<>(analysis.blocks);
                analysis.blocks.clear();

                MMUtils.forEachArrayOffset(config.arraySpan, analysis.deltas, d -> {
                    t.apply(d);

                    for (PendingBlock original : base) {
                        PendingBlock dup = original.clone();
                        dup.x += d.x;
                        dup.y += d.y;
                        dup.z += d.z;
                        analysis.blocks.add(dup);
                    }
                });
            }

            analysis.deltas = t.apply(analysis.deltas);

            t.uncacheRotation();
        } else {
            for (PendingBlock block : analysis.blocks) {
                block.x += coordC.x;
                block.y += coordC.y;
                block.z += coordC.z;
            }
        }

        return analysis.blocks;
    }

    private List<PendingBlock> getExchangeBlocks(ManipulatorTier tier, Level world) {
        Location coordA = config.coordA;
        Location coordB = config.coordB;

        if (!Location.areCompatible(coordA, coordB) || !coordA.isInWorld(world)) return new ArrayList<>();

        if (config.replaceWhitelist == null || config.replaceWhitelist.specs.isEmpty()) return new ArrayList<>();

        Vector3i deltas = MMUtils.getRegionDeltas(coordA, coordB);

        ArrayList<PendingBlock> pending = new ArrayList<>();

        Random rng = new Random(config.hashCode());

        BlockSpec existing = new BlockSpec();

        boolean replacingAir = config.replaceWhitelist.contains(BlockSpec.air());

        for (Vector3i voxel : MMUtils.getBlocksInBB(coordA, deltas)) {
            int x = voxel.x;
            int y = voxel.y;
            int z = voxel.z;

            BlockPos pos = new BlockPos(x, y, z);

            if (!replacingAir) {
                if (world.isEmptyBlock(pos)) continue;
            }

            BlockSpec.fromBlock(existing, world, pos);

            if (!replacingAir) {
                if (existing.isAir()) continue;
            }

            // get the cable part if possible (ae cables are parts in a cable bus)
            CableHandlers.getCableInWorld(existing, world, pos);

            if (!config.replaceWhitelist.containsEquivalent(existing)) continue;

            ImmutableBlockSpec replacement = config.replaceWith.get(rng);

            PendingBlock rep = CableHandlers.instantiateExchange(this, replacement, world, pos);

            if (rep == null) {
                rep = replacement.instantiate(world, x, y, z);

                rep.analyze(world.getBlockEntity(pos), PendingBlock.ANALYZE_ALL);
                rep.migrate();
            }

            pending.add(rep);
        }

        return pending;
    }

    private List<PendingBlock> getCableBlocks(Level world) {
        Location coordA = config.coordA;
        Location coordB = config.coordB;

        if (!Location.areCompatible(coordA, coordB) || !coordA.isInWorld(world)) return new ArrayList<>();

        Vector3i a = coordA.toVec();
        Vector3i b = pinToAxes(a, coordB.toVec());

        ArrayList<PendingBlock> out = new ArrayList<>();

        List<Vector3i> voxels = getLineVoxels(a.x, a.y, a.z, b.x, b.y, b.z);

        if (config.cables == null) {
            for (Vector3i voxel : voxels) {
                PendingBlock removal = CableHandlers.getCableRemoval(world, new BlockPos(voxel.x, voxel.y, voxel.z));

                out.add(removal != null ? removal : BlockSpec.AIR.instantiate(world, voxel.x, voxel.y, voxel.z));
            }
        } else {
            CableHandlers.getCables(a, b, voxels, out, world, config.cables);
        }

        return out;
    }

    private List<PendingBlock> getGeomPendingBlocks(Level world) {
        Location coordA = config.coordA;
        Location coordB = config.coordB;
        Location coordC = config.coordC;

        if (!Location.areCompatible(coordA, coordB) || !coordA.isInWorld(world)) return new ArrayList<>();

        if (config.shape.requiresC()) {
            if (!Location.areCompatible(coordA, coordC) || !coordA.isInWorld(world)) return new ArrayList<>();
        }

        int x1 = config.coordA.x;
        int y1 = config.coordA.y;
        int z1 = config.coordA.z;
        int x2 = config.coordB.x;
        int y2 = config.coordB.y;
        int z2 = config.coordB.z;

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);

        ArrayList<PendingBlock> pending = new ArrayList<>();

        switch (config.shape) {
            case LINE -> iterateLine(pending, x1, y1, z1, x2, y2, z2);
            case CUBE -> iterateCube(pending, minX, minY, minZ, maxX, maxY, maxZ);
            case SPHERE -> iterateSphere(pending, minX, minY, minZ, maxX, maxY, maxZ);
            case CYLINDER -> iterateCylinder(pending, coordA.toVec(), coordB.toVec(), coordC.toVec());
        }

        return pending;
    }

    public static List<Vector3i> getLineVoxels(int x1, int y1, int z1, int x2, int y2, int z2) {
        List<Vector3i> voxels = new ArrayList<>();

        int dx = Math.abs(x1 - x2), dy = Math.abs(y1 - y2), dz = Math.abs(z1 - z2);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1, sz = z1 < z2 ? 1 : -1;

        voxels.add(new Vector3i(x1, y1, z1));

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;

            while (x1 != x2) {
                x1 += sx;

                if (p1 >= 0) {
                    y1 += sy;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += sz;
                    p2 -= 2 * dx;
                }

                p1 += 2 * dy;
                p2 += 2 * dz;

                voxels.add(new Vector3i(x1, y1, z1));
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;

            while (y1 != y2) {
                y1 += sy;

                if (p1 >= 0) {
                    x1 += sx;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += sz;
                    p2 -= 2 * dy;
                }

                p1 += 2 * dx;
                p2 += 2 * dz;

                voxels.add(new Vector3i(x1, y1, z1));
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;

            while (z1 != z2) {
                z1 += sz;

                if (p1 >= 0) {
                    y1 += sy;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += sx;
                    p2 -= 2 * dz;
                }

                p1 += 2 * dy;
                p2 += 2 * dx;

                voxels.add(new Vector3i(x1, y1, z1));
            }
        }

        return voxels;
    }

    private void iterateLine(ArrayList<PendingBlock> pending, int x1, int y1, int z1, int x2, int y2, int z2) {
        Random rng = new Random(config.hashCode());

        for (Vector3i voxel : getLineVoxels(x1, y1, z1, x2, y2, z2)) {
            pending.add(config.edges.get(rng).instantiate(config.coordA.worldId, voxel.x, voxel.y, voxel.z));
        }
    }

    private void iterateCube(ArrayList<PendingBlock> pending, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Random rng = new Random(config.hashCode());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int insideCount = 0;

                    if (x > minX && x < maxX) insideCount++;
                    if (y > minY && y < maxY) insideCount++;
                    if (z > minZ && z < maxZ) insideCount++;

                    ImmutableBlockSpec spec = switch (insideCount) {
                        case 0 -> config.corners.get(rng);
                        case 1 -> config.edges.get(rng);
                        case 2 -> config.faces.get(rng);
                        case 3 -> config.volumes.get(rng);
                        default -> BlockSpec.AIR;
                    };

                    pending.add(spec.instantiate(config.coordA.worldId, x, y, z).setOrders(insideCount, insideCount));
                }
            }
        }
    }

    private void iterateSphere(ArrayList<PendingBlock> pending, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        Random rng = new Random(config.hashCode());

        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;

        double rx = sx / 2.0;
        double ry = sy / 2.0;
        double rz = sz / 2.0;

        boolean[][][] present = new boolean[sx + 2][sy + 2][sz + 2];

        for (int x = 0; x < sx; x++) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    // the ternaries here check whether the given axis is 1, in which case this is a circle and not a
                    // sphere
                    // spotless:off
                    double distance = Math.sqrt(
                        (rx > 1 ? Math.pow((x - rx + 0.5) / rx, 2.0) : 0) +
                            (ry > 1 ? Math.pow((y - ry + 0.5) / ry, 2.0) : 0) +
                            (rz > 1 ? Math.pow((z - rz + 0.5) / rz, 2.0) : 0)
                    );
                    // spotless:on

                    if (distance <= 1) {
                        PendingBlock block = config.volumes.get(rng)
                            .instantiate(config.coordA.worldId, x + minX, y + minY, z + minZ)
                            .setOrders(1, 1);

                        present[x + 1][y + 1][z + 1] = true;
                        pending.add(block);
                    }
                }
            }
        }

        ArrayList<Direction> directions = new ArrayList<>();

        if (rx > 1) {
            directions.add(Direction.EAST);
            directions.add(Direction.WEST);
        }

        if (ry > 1) {
            directions.add(Direction.UP);
            directions.add(Direction.DOWN);
        }

        if (rz > 1) {
            directions.add(Direction.NORTH);
            directions.add(Direction.SOUTH);
        }

        for (PendingBlock block : pending) {
            for (Direction dir : directions) {
                if (!present[block.x - minX + 1 + dir.getStepX()][block.y - minY + 1 + dir.getStepY()][block.z - minZ + 1 +
                    dir.getStepZ()]) {
                    block.setBlock(config.faces.get(rng));
                    block.buildOrder = 0;
                    block.renderOrder = 0;
                    break;
                }
            }
        }
    }

    private void iterateCylinder(ArrayList<PendingBlock> pending, Vector3i coordA, Vector3i coordB, Vector3i coordC) {
        Random rng = new Random(config.hashCode());

        Vector3i b2 = pinToPlanes(coordA, coordB);
        Vector3i height = pinToLine(coordA, b2, coordC).sub(coordA);

        Vector3i delta = new Vector3i(b2).sub(coordA);

        delta.x += signum(delta.x);
        delta.y += signum(delta.y);
        delta.z += signum(delta.z);

        // the deltas for each dimension (A/B/Height)
        int dA, dB, dH;
        // used to determine the final block position
        Vector3i vecA, vecB, vecH;

        // calculate the delta vectors for each axis
        // this is kinda cursed and I don't really understand it anymore, so good luck changing it
        switch (delta.minComponent()) {
            case 0 -> {
                dA = delta.y;
                dB = delta.z;
                dH = height.x;
                vecA = new Vector3i(0, signum(delta.y), 0);
                vecB = new Vector3i(0, 0, signum(delta.z));
                vecH = new Vector3i(signum(height.x), 0, 0);
            }
            case 1 -> {
                dA = delta.x;
                dB = delta.z;
                dH = height.y;
                vecA = new Vector3i(signum(delta.x), 0, 0);
                vecB = new Vector3i(0, 0, signum(delta.z));
                vecH = new Vector3i(0, signum(height.y), 0);
            }
            case 2 -> {
                dA = delta.x;
                dB = delta.y;
                dH = height.z;
                vecA = new Vector3i(signum(delta.x), 0, 0);
                vecB = new Vector3i(0, signum(delta.y), 0);
                vecH = new Vector3i(0, 0, signum(height.z));
            }
            default -> throw new AssertionError();
        }

        int absA = Math.abs(dA);
        int absB = Math.abs(dB);
        int absH = Math.abs(dH) + 1; // I have no idea why this +1 is needed

        float rA = absA / 2f;
        float rB = absB / 2f;

        boolean[][][] present = new boolean[absA + 2][absH + 2][absB + 2];

        // generate the blocks in A,B,H space
        // at this point, x=A, z=B, and y=H
        for (int a = 0; a < absA; a++) {
            for (int b = 0; b < absB; b++) {
                double distance = Math.pow((a - rA + 0.5) / rA, 2.0) + Math.pow((b - rB + 0.5) / rB, 2.0);

                if (distance <= 1) {
                    for (int h = 0; h < absH; h++) {
                        PendingBlock block = config.volumes.get(rng)
                            .instantiate(config.coordA.worldId, a, h, b)
                            .setOrders(2, 0);

                        present[a + 1][h + 1][b + 1] = true;
                        pending.add(block);
                    }
                }
            }
        }

        // check the adjacent blocks for each block and determine whether the block should be a volume, edge, or face
        for (PendingBlock block : pending) {
            byte adj = 0;

            for (Direction dir : Direction.values()) {
                if (present[block.x + 1 + dir.getStepX()][block.y + 1 + dir.getStepY()][block.z + 1 + dir.getStepZ()]) {
                    adj |= (byte) (1 << dir.ordinal());
                }
            }

            // if this block is missing an adjacent block, it's not a volume
            if (adj != 0b111111) {
                // if this block is missing one of the N/S/E/W blocks, it's an edge (the surface)
                if ((adj & 0b111100) == 0b111100) {
                    block.setBlock(config.edges.get(rng));
                    block.buildOrder = 1;
                    block.renderOrder = 1;
                } else {
                    // otherwise, it's a face (top & bottom)
                    block.setBlock(config.faces.get(rng));
                    block.buildOrder = 2;
                    block.renderOrder = 0;
                }
            }
        }

        // transform the positions of each block from relative A,B,H space into absolute X,Y,Z space
        for (PendingBlock block : pending) {
            int a = block.x, b = block.z, h = block.y;

            // why, yes, that is an integer matrix
            block.x = a * vecA.x + b * vecB.x + h * vecH.x + coordA.x;
            block.y = a * vecA.y + b * vecB.y + h * vecH.y + coordA.y;
            block.z = a * vecA.z + b * vecB.z + h * vecH.z + coordA.z;
        }
    }

    // #endregion

    /**
     * Pins a point to the axis planes around an origin.
     *
     * @return The pinned point
     */
    public static Vector3i pinToPlanes(Vector3i origin, Vector3i point) {
        int dX = Math.abs(point.x - origin.x);
        int dY = Math.abs(point.y - origin.y);
        int dZ = Math.abs(point.z - origin.z);

        int shortest = min(dX, dY, dZ);

        if (shortest == dX) {
            return new Vector3i(origin.x, point.y, point.z);
        } else if (shortest == dY) {
            return new Vector3i(point.x, origin.y, point.z);
        } else {
            return new Vector3i(point.x, point.y, origin.z);
        }
    }

    /**
     * Pins a point to the normal of the axis plane described by origin,b.
     *
     * @param origin The origin
     * @param b      A point on an axis plane of origin
     * @param point  The point to pin
     * @return The pinned point on the normal
     */
    public static Vector3i pinToLine(Vector3i origin, Vector3i b, Vector3i point) {
        return switch (new Vector3i(b).sub(origin).minComponent()) {
            case 0 -> new Vector3i(point.x, origin.y, origin.z);
            case 1 -> new Vector3i(origin.x, point.y, origin.z);
            case 2 -> new Vector3i(origin.x, origin.y, point.z);
            default -> throw new AssertionError();
        };
    }

    /**
     * Pins a point to the cardinal axes.
     */
    public static Vector3i pinToAxes(Vector3i origin, Vector3i point) {
        return switch (new Vector3i(point).sub(origin).maxComponent()) {
            case 0 -> new Vector3i(point.x, origin.y, origin.z);
            case 1 -> new Vector3i(origin.x, point.y, origin.z);
            case 2 -> new Vector3i(origin.x, origin.y, point.z);
            default -> throw new AssertionError();
        };
    }

    public boolean hasUpgrade(MMUpgrades upgrade) {
        return upgrade != null && installedUpgrades.get(upgrade.bit);
    }

    public boolean installUpgrade(MMUpgrades upgrade) {
        if (installedUpgrades.get(upgrade.bit)) return false;

        installedUpgrades.set(upgrade.bit);
        upgradeProvidedCapabilities |= upgrade.providesCaps;

        return true;
    }

    public boolean couldAcceptUpgrade(ManipulatorTier tier, MMUpgrades upgrade) {
        return tier.allowedUpgrades.contains(upgrade) && !installedUpgrades.get(upgrade.bit);
    }

    public Set<MMUpgrades> getInstalledUpgrades() {
        EnumSet<MMUpgrades> set = EnumSet.noneOf(MMUpgrades.class);

        for (MMUpgrades upgrade : MMUpgrades.values()) {
            if (hasUpgrade(upgrade)) set.add(upgrade);
        }

        return set;
    }

    public enum Shape {

        LINE,
        CUBE,
        SPHERE,
        CYLINDER;

        public boolean requiresC() {
            return switch (this) {
                case LINE, CUBE, SPHERE -> false;
                case CYLINDER -> true;
            };
        }
    }

    public enum PendingAction {
        MOVING_COORDS,
        MARK_COPY_A,
        MARK_COPY_B,
        MARK_CUT_A,
        MARK_CUT_B,
        MARK_PASTE,
        GEOM_SELECTING_BLOCK,
        EXCH_SET_TARGET,
        EXCH_ADD_REPLACE,
        EXCH_SET_REPLACE,
        PICK_CABLE,
        MARK_ARRAY,
    }

    public enum BlockSelectMode {
        CORNERS,
        EDGES,
        FACES,
        VOLUMES,
        ALL,
    }

    public enum BlockRemoveMode {
        NONE,
        REPLACEABLE,
        ALL
    }

    public enum PlaceMode {
        GEOMETRY,
        MOVING,
        COPYING,
        EXCHANGING,
        CABLES,
    }

    public static ItemStack empty() {
        return ItemStack.EMPTY;
    }
}
