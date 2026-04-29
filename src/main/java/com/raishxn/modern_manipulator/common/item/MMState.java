package com.raishxn.modern_manipulator.common.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.raishxn.modern_manipulator.common.building.BlockMovers;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MMState {

    public static final String TAG_NAME = "MMState";

    private static final String TAG_COORD_A = "coordA";
    private static final String TAG_COORD_B = "coordB";
    private static final String TAG_COORD_C = "coordC";
    private static final String TAG_ME_DOWNLINK = "meDownlink";
    private static final String TAG_MODE = "mode";
    private static final String TAG_SHAPE = "shape";
    private static final String TAG_INSTALLED_UPGRADES = "installedUpgrades";
    private static final String TAG_PENDING_ACTION = "pendingAction";
    private static final String TAG_BLUEPRINT = "blueprint";
    private static final String TAG_ROTATION_Y = "rotationY";
    private static final String TAG_MIRROR_X = "mirrorX";
    private static final String TAG_MIRROR_Y = "mirrorY";
    private static final String TAG_MIRROR_Z = "mirrorZ";
    private static final String TAG_REMOVE_MODE = "removeMode";
    private static final String TAG_BLOCK_SELECT_MODE = "blockSelectMode";
    private static final String TAG_EXCHANGE_REPLACEMENT = "exchangeReplacement";
    private static final String TAG_EXCHANGE_REPLACEMENT_STATE = "exchangeReplacementState";
    private static final String TAG_EXCHANGE_WHITELIST = "exchangeWhitelist";
    private static final String TAG_CABLE_STACK = "cableStack";
    private static final String TAG_CABLE_STATE = "cableState";
    private static final String TAG_PASTE_ARRAY_X = "pasteArrayX";
    private static final String TAG_PASTE_ARRAY_Y = "pasteArrayY";
    private static final String TAG_PASTE_ARRAY_Z = "pasteArrayZ";
    private static final String TAG_PASTE_OFFSET_X = "pasteOffsetX";
    private static final String TAG_PASTE_OFFSET_Y = "pasteOffsetY";
    private static final String TAG_PASTE_OFFSET_Z = "pasteOffsetZ";
    private static final int MAX_PASTE_ARRAY = 16;
    private static final int MAX_PASTE_OFFSET = 256;

    private @Nullable MarkedPosition coordA;
    private @Nullable MarkedPosition coordB;
    private @Nullable MarkedPosition coordC;
    private @Nullable MarkedPosition meDownlink;
    private ToolMode mode = ToolMode.GEOMETRY;
    private Shape shape = Shape.CUBE;
    private int installedUpgrades;
    private @Nullable PendingAction pendingAction;
    private @Nullable Blueprint blueprint;
    private int rotationY;
    private boolean mirrorX;
    private boolean mirrorY;
    private boolean mirrorZ;
    private RemoveMode removeMode = RemoveMode.ALL;
    private BlockSelectMode blockSelectMode = BlockSelectMode.ALL;
    private ItemStack exchangeReplacement = ItemStack.EMPTY;
    private @Nullable BlockState exchangeReplacementState;
    private final List<BlockState> exchangeWhitelist = new java.util.ArrayList<>();
    private ItemStack cableStack = ItemStack.EMPTY;
    private @Nullable BlockState cableState;
    private int pasteArrayX = 1;
    private int pasteArrayY = 1;
    private int pasteArrayZ = 1;
    private int pasteOffsetX;
    private int pasteOffsetY;
    private int pasteOffsetZ;

    public static MMState getOrCreate(CompoundTag root) {
        if (!root.contains(TAG_NAME, Tag.TAG_COMPOUND)) {
            MMState state = new MMState();
            root.put(TAG_NAME, state.save());
            return state;
        }
        return load(root.getCompound(TAG_NAME));
    }

    public static MMState load(CompoundTag tag) {
        MMState state = new MMState();
        if (tag.contains(TAG_COORD_A, Tag.TAG_COMPOUND)) {
            state.coordA = MarkedPosition.load(tag.getCompound(TAG_COORD_A));
        }
        if (tag.contains(TAG_COORD_B, Tag.TAG_COMPOUND)) {
            state.coordB = MarkedPosition.load(tag.getCompound(TAG_COORD_B));
        }
        if (tag.contains(TAG_COORD_C, Tag.TAG_COMPOUND)) {
            state.coordC = MarkedPosition.load(tag.getCompound(TAG_COORD_C));
        }
        if (tag.contains(TAG_ME_DOWNLINK, Tag.TAG_COMPOUND)) {
            state.meDownlink = MarkedPosition.load(tag.getCompound(TAG_ME_DOWNLINK));
        }
        state.mode = ToolMode.byName(tag.getString(TAG_MODE));
        state.shape = Shape.byName(tag.getString(TAG_SHAPE));
        state.installedUpgrades = tag.getInt(TAG_INSTALLED_UPGRADES);
        state.rotationY = Math.floorMod(tag.getInt(TAG_ROTATION_Y), 4);
        state.mirrorX = tag.getBoolean(TAG_MIRROR_X);
        state.mirrorY = tag.getBoolean(TAG_MIRROR_Y);
        state.mirrorZ = tag.getBoolean(TAG_MIRROR_Z);
        state.removeMode = RemoveMode.byName(tag.getString(TAG_REMOVE_MODE));
        state.blockSelectMode = BlockSelectMode.byName(tag.getString(TAG_BLOCK_SELECT_MODE));
        if (tag.contains(TAG_EXCHANGE_REPLACEMENT, Tag.TAG_COMPOUND)) {
            state.exchangeReplacement = ItemStack.of(tag.getCompound(TAG_EXCHANGE_REPLACEMENT));
        }
        if (tag.contains(TAG_EXCHANGE_REPLACEMENT_STATE, Tag.TAG_COMPOUND)) {
            state.exchangeReplacementState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    tag.getCompound(TAG_EXCHANGE_REPLACEMENT_STATE));
            if (state.exchangeReplacementState.isAir()) {
                state.exchangeReplacementState = null;
            }
        }
        ListTag exchangeWhitelistTag = tag.getList(TAG_EXCHANGE_WHITELIST, Tag.TAG_COMPOUND);
        for (int i = 0; i < exchangeWhitelistTag.size(); i++) {
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    exchangeWhitelistTag.getCompound(i));
            if (!blockState.isAir()) {
                state.addExchangeWhitelistBlock(blockState);
            }
        }
        if (tag.contains(TAG_CABLE_STACK, Tag.TAG_COMPOUND)) {
            state.cableStack = ItemStack.of(tag.getCompound(TAG_CABLE_STACK));
        }
        if (tag.contains(TAG_CABLE_STATE, Tag.TAG_COMPOUND)) {
            state.cableState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    tag.getCompound(TAG_CABLE_STATE));
            if (state.cableState.isAir()) {
                state.cableState = null;
            }
        }
        state.pasteArrayX = clampPasteArray(tag.contains(TAG_PASTE_ARRAY_X, Tag.TAG_INT) ?
                tag.getInt(TAG_PASTE_ARRAY_X) : 1);
        state.pasteArrayY = clampPasteArray(tag.contains(TAG_PASTE_ARRAY_Y, Tag.TAG_INT) ?
                tag.getInt(TAG_PASTE_ARRAY_Y) : 1);
        state.pasteArrayZ = clampPasteArray(tag.contains(TAG_PASTE_ARRAY_Z, Tag.TAG_INT) ?
                tag.getInt(TAG_PASTE_ARRAY_Z) : 1);
        state.pasteOffsetX = clampPasteOffset(tag.getInt(TAG_PASTE_OFFSET_X));
        state.pasteOffsetY = clampPasteOffset(tag.getInt(TAG_PASTE_OFFSET_Y));
        state.pasteOffsetZ = clampPasteOffset(tag.getInt(TAG_PASTE_OFFSET_Z));
        if (tag.contains(TAG_PENDING_ACTION, Tag.TAG_COMPOUND)) {
            state.pendingAction = PendingAction.load(tag.getCompound(TAG_PENDING_ACTION));
        }
        if (tag.contains(TAG_BLUEPRINT, Tag.TAG_COMPOUND)) {
            state.blueprint = Blueprint.load(tag.getCompound(TAG_BLUEPRINT));
        }
        return state;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (coordA != null) {
            tag.put(TAG_COORD_A, coordA.save());
        }
        if (coordB != null) {
            tag.put(TAG_COORD_B, coordB.save());
        }
        if (coordC != null) {
            tag.put(TAG_COORD_C, coordC.save());
        }
        if (meDownlink != null) {
            tag.put(TAG_ME_DOWNLINK, meDownlink.save());
        }
        tag.putString(TAG_MODE, mode.serializedName);
        tag.putString(TAG_SHAPE, shape.serializedName);
        tag.putInt(TAG_INSTALLED_UPGRADES, installedUpgrades);
        tag.putInt(TAG_ROTATION_Y, rotationY);
        tag.putBoolean(TAG_MIRROR_X, mirrorX);
        tag.putBoolean(TAG_MIRROR_Y, mirrorY);
        tag.putBoolean(TAG_MIRROR_Z, mirrorZ);
        tag.putString(TAG_REMOVE_MODE, removeMode.serializedName);
        tag.putString(TAG_BLOCK_SELECT_MODE, blockSelectMode.serializedName);
        if (!exchangeReplacement.isEmpty()) {
            tag.put(TAG_EXCHANGE_REPLACEMENT, exchangeReplacement.save(new CompoundTag()));
        }
        if (exchangeReplacementState != null) {
            tag.put(TAG_EXCHANGE_REPLACEMENT_STATE, NbtUtils.writeBlockState(exchangeReplacementState));
        }
        ListTag exchangeWhitelistTag = new ListTag();
        for (BlockState blockState : exchangeWhitelist) {
            exchangeWhitelistTag.add(NbtUtils.writeBlockState(blockState));
        }
        tag.put(TAG_EXCHANGE_WHITELIST, exchangeWhitelistTag);
        if (!cableStack.isEmpty()) {
            tag.put(TAG_CABLE_STACK, cableStack.save(new CompoundTag()));
        }
        if (cableState != null) {
            tag.put(TAG_CABLE_STATE, NbtUtils.writeBlockState(cableState));
        }
        tag.putInt(TAG_PASTE_ARRAY_X, pasteArrayX);
        tag.putInt(TAG_PASTE_ARRAY_Y, pasteArrayY);
        tag.putInt(TAG_PASTE_ARRAY_Z, pasteArrayZ);
        tag.putInt(TAG_PASTE_OFFSET_X, pasteOffsetX);
        tag.putInt(TAG_PASTE_OFFSET_Y, pasteOffsetY);
        tag.putInt(TAG_PASTE_OFFSET_Z, pasteOffsetZ);
        if (pendingAction != null) {
            tag.put(TAG_PENDING_ACTION, pendingAction.save());
        }
        if (blueprint != null) {
            tag.put(TAG_BLUEPRINT, blueprint.save());
        }
        return tag;
    }

    public void saveInto(CompoundTag root) {
        root.put(TAG_NAME, save());
    }

    public @Nullable MarkedPosition coordA() {
        return coordA;
    }

    public @Nullable MarkedPosition coordB() {
        return coordB;
    }

    public @Nullable MarkedPosition coordC() {
        return coordC;
    }

    public @Nullable MarkedPosition meDownlink() {
        return meDownlink;
    }

    public ToolMode mode() {
        return mode;
    }

    public Shape shape() {
        return shape;
    }

    public int installedUpgrades() {
        return installedUpgrades;
    }

    public @Nullable MMSelection selection() {
        if (coordA == null || coordB == null) {
            return null;
        }
        return MMSelection.from(coordA, coordB, shape);
    }

    public @Nullable PendingAction pendingAction() {
        return pendingAction;
    }

    public @Nullable Blueprint blueprint() {
        return blueprint;
    }

    public int rotationY() {
        return rotationY;
    }

    public boolean mirrorX() {
        return mirrorX;
    }

    public boolean mirrorY() {
        return mirrorY;
    }

    public boolean mirrorZ() {
        return mirrorZ;
    }

    public RemoveMode removeMode() {
        return removeMode;
    }

    public BlockSelectMode blockSelectMode() {
        return blockSelectMode;
    }

    public ItemStack exchangeReplacement() {
        return exchangeReplacement.copy();
    }

    public @Nullable BlockState exchangeReplacementState() {
        return exchangeReplacementState;
    }

    public List<BlockState> exchangeWhitelist() {
        return List.copyOf(exchangeWhitelist);
    }

    public ItemStack cableStack() {
        return cableStack.copy();
    }

    public @Nullable BlockState cableState() {
        return cableState;
    }

    public boolean hasExchangeWhitelist() {
        return !exchangeWhitelist.isEmpty();
    }

    public boolean isExchangeWhitelisted(BlockState blockState) {
        for (BlockState whitelistedState : exchangeWhitelist) {
            if (whitelistedState.equals(blockState)) {
                return true;
            }
        }
        return false;
    }

    public int pasteArrayX() {
        return pasteArrayX;
    }

    public int pasteArrayY() {
        return pasteArrayY;
    }

    public int pasteArrayZ() {
        return pasteArrayZ;
    }

    public long pasteArrayCopies() {
        return (long) pasteArrayX * pasteArrayY * pasteArrayZ;
    }

    public int pasteOffsetX() {
        return pasteOffsetX;
    }

    public int pasteOffsetY() {
        return pasteOffsetY;
    }

    public int pasteOffsetZ() {
        return pasteOffsetZ;
    }

    public boolean hasPasteOffset() {
        return pasteOffsetX != 0 || pasteOffsetY != 0 || pasteOffsetZ != 0;
    }

    public void startPendingAction(PendingAction pendingAction) {
        this.pendingAction = pendingAction;
    }

    public void clearPendingAction() {
        this.pendingAction = null;
    }

    public boolean hasUpgrade(MMUpgrade upgrade) {
        return (installedUpgrades & upgrade.mask()) != 0;
    }

    public void installUpgrade(MMUpgrade upgrade) {
        installedUpgrades |= upgrade.mask();
    }

    public boolean hasCapability(MatterManipulatorItem.ManipulatorTier tier, MMCapability capability) {
        if (tier.hasBaseCapability(capability)) {
            return true;
        }
        for (MMUpgrade upgrade : MMUpgrade.values()) {
            if (hasUpgrade(upgrade) && upgrade.isAllowedOn(tier) &&
                    upgrade.providedCapabilities().contains(capability)) {
                return true;
            }
        }
        return false;
    }

    public void setCoordA(MarkedPosition coordA) {
        this.coordA = coordA;
    }

    public void setCoordB(MarkedPosition coordB) {
        this.coordB = coordB;
    }

    public void setCoordC(MarkedPosition coordC) {
        this.coordC = coordC;
    }

    public void setMeDownlink(MarkedPosition meDownlink) {
        this.meDownlink = meDownlink;
    }

    public void clearMeDownlink() {
        this.meDownlink = null;
    }

    public void clearCoordC() {
        this.coordC = null;
    }

    public void clearCoords() {
        this.coordA = null;
        this.coordB = null;
        this.coordC = null;
    }

    public void setMode(ToolMode mode) {
        this.mode = mode;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
    }

    public void setBlueprint(@Nullable Blueprint blueprint) {
        this.blueprint = blueprint;
    }

    public void clearBlueprint() {
        this.blueprint = null;
    }

    public void rotatePaste(int delta) {
        rotationY = Math.floorMod(rotationY + delta, 4);
    }

    public void toggleMirrorX() {
        mirrorX = !mirrorX;
    }

    public void toggleMirrorY() {
        mirrorY = !mirrorY;
    }

    public void toggleMirrorZ() {
        mirrorZ = !mirrorZ;
    }

    public void resetTransform() {
        rotationY = 0;
        mirrorX = false;
        mirrorY = false;
        mirrorZ = false;
        resetPasteOffset();
    }

    public void adjustPasteArray(int deltaX, int deltaY, int deltaZ) {
        pasteArrayX = clampPasteArray(pasteArrayX + deltaX);
        pasteArrayY = clampPasteArray(pasteArrayY + deltaY);
        pasteArrayZ = clampPasteArray(pasteArrayZ + deltaZ);
    }

    public void resetPasteArray() {
        pasteArrayX = 1;
        pasteArrayY = 1;
        pasteArrayZ = 1;
    }

    public void adjustPasteOffset(int deltaX, int deltaY, int deltaZ) {
        pasteOffsetX = clampPasteOffset(pasteOffsetX + deltaX);
        pasteOffsetY = clampPasteOffset(pasteOffsetY + deltaY);
        pasteOffsetZ = clampPasteOffset(pasteOffsetZ + deltaZ);
    }

    public void resetPasteOffset() {
        pasteOffsetX = 0;
        pasteOffsetY = 0;
        pasteOffsetZ = 0;
    }

    public void setRemoveMode(RemoveMode removeMode) {
        this.removeMode = removeMode;
    }

    public void setBlockSelectMode(BlockSelectMode blockSelectMode) {
        this.blockSelectMode = blockSelectMode;
    }

    public void setExchangeReplacement(ItemStack stack, BlockState blockState) {
        exchangeReplacement = stack.copyWithCount(1);
        exchangeReplacementState = blockState;
    }

    public void clearExchangeReplacement() {
        exchangeReplacement = ItemStack.EMPTY;
        exchangeReplacementState = null;
    }

    public void addExchangeWhitelistBlock(BlockState blockState) {
        if (blockState.isAir()) {
            return;
        }
        for (BlockState existingState : exchangeWhitelist) {
            if (existingState.equals(blockState)) {
                return;
            }
        }
        exchangeWhitelist.add(blockState);
    }

    public void clearExchangeWhitelist() {
        exchangeWhitelist.clear();
    }

    public void setCable(ItemStack stack, BlockState blockState) {
        cableStack = stack.copyWithCount(1);
        cableState = blockState;
    }

    public void clearCable() {
        cableStack = ItemStack.EMPTY;
        cableState = null;
    }

    public int pasteSizeX(Blueprint blueprint) {
        return rotationY % 2 == 0 ? blueprint.sizeX() : blueprint.sizeZ();
    }

    public int pasteSizeZ(Blueprint blueprint) {
        return rotationY % 2 == 0 ? blueprint.sizeZ() : blueprint.sizeX();
    }

    public int pasteArraySizeX(Blueprint blueprint) {
        return pasteSizeX(blueprint) * pasteArrayX;
    }

    public int pasteArraySizeY(Blueprint blueprint) {
        return blueprint.sizeY() * pasteArrayY;
    }

    public int pasteArraySizeZ(Blueprint blueprint) {
        return pasteSizeZ(blueprint) * pasteArrayZ;
    }

    public BlockPos pasteOrigin(BlockPos coordC) {
        return coordC.offset(pasteOffsetX, pasteOffsetY, pasteOffsetZ);
    }

    private static int clampPasteArray(int value) {
        return Math.max(1, Math.min(MAX_PASTE_ARRAY, value));
    }

    private static int clampPasteOffset(int value) {
        return Math.max(-MAX_PASTE_OFFSET, Math.min(MAX_PASTE_OFFSET, value));
    }

    public BlockPos transformedRelative(Blueprint blueprint, BlueprintBlock block) {
        int x = mirrorX ? blueprint.sizeX() - 1 - block.x() : block.x();
        int y = mirrorY ? blueprint.sizeY() - 1 - block.y() : block.y();
        int z = mirrorZ ? blueprint.sizeZ() - 1 - block.z() : block.z();
        return switch (rotationY) {
            case 1 -> new BlockPos(blueprint.sizeZ() - 1 - z, y, x);
            case 2 -> new BlockPos(blueprint.sizeX() - 1 - x, y, blueprint.sizeZ() - 1 - z);
            case 3 -> new BlockPos(z, y, blueprint.sizeX() - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    public BlueprintBlock transformedBlock(Blueprint blueprint, BlueprintBlock block) {
        BlockPos relative = transformedRelative(blueprint, block);
        BlockState transformedState = block.state();
        if (mirrorX) {
            transformedState = transformedState.mirror(Mirror.FRONT_BACK);
        }
        if (mirrorZ) {
            transformedState = transformedState.mirror(Mirror.LEFT_RIGHT);
        }
        transformedState = switch (rotationY) {
            case 1 -> transformedState.rotate(Rotation.CLOCKWISE_90);
            case 2 -> transformedState.rotate(Rotation.CLOCKWISE_180);
            case 3 -> transformedState.rotate(Rotation.COUNTERCLOCKWISE_90);
            default -> transformedState;
        };
        if (mirrorY) {
            transformedState = mirrorVerticalDirections(transformedState);
        }
        return new BlueprintBlock(relative.getX(), relative.getY(), relative.getZ(), transformedState,
                BlockMovers.transformBlockEntityTag(block.blockEntityTag(), mirrorX, mirrorY, mirrorZ, rotationY));
    }

    private static BlockState mirrorVerticalDirections(BlockState state) {
        BlockState result = state;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty) {
                Direction direction = result.getValue(directionProperty);
                if (direction.getAxis() == Direction.Axis.Y) {
                    result = result.setValue(directionProperty, direction.getOpposite());
                }
            }
        }
        return result;
    }

    public record Blueprint(int sizeX, int sizeY, int sizeZ, List<BlueprintBlock> blocks, boolean consumesItems,
                            @Nullable ResourceLocation sourceDimension, @Nullable BlockPos sourceMin) {

        private static final String TAG_SIZE_X = "sizeX";
        private static final String TAG_SIZE_Y = "sizeY";
        private static final String TAG_SIZE_Z = "sizeZ";
        private static final String TAG_BLOCKS = "blocks";
        private static final String TAG_CONSUMES_ITEMS = "consumesItems";
        private static final String TAG_SOURCE_DIMENSION = "sourceDimension";
        private static final String TAG_SOURCE_X = "sourceX";
        private static final String TAG_SOURCE_Y = "sourceY";
        private static final String TAG_SOURCE_Z = "sourceZ";

        public Blueprint(int sizeX, int sizeY, int sizeZ, List<BlueprintBlock> blocks, boolean consumesItems) {
            this(sizeX, sizeY, sizeZ, blocks, consumesItems, null, null);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_SIZE_X, sizeX);
            tag.putInt(TAG_SIZE_Y, sizeY);
            tag.putInt(TAG_SIZE_Z, sizeZ);
            tag.putBoolean(TAG_CONSUMES_ITEMS, consumesItems);
            if (sourceDimension != null && sourceMin != null) {
                tag.putString(TAG_SOURCE_DIMENSION, sourceDimension.toString());
                tag.putInt(TAG_SOURCE_X, sourceMin.getX());
                tag.putInt(TAG_SOURCE_Y, sourceMin.getY());
                tag.putInt(TAG_SOURCE_Z, sourceMin.getZ());
            }
            ListTag blockList = new ListTag();
            for (BlueprintBlock block : blocks) {
                blockList.add(block.save());
            }
            tag.put(TAG_BLOCKS, blockList);
            return tag;
        }

        public static Blueprint load(CompoundTag tag) {
            List<BlueprintBlock> blocks = new java.util.ArrayList<>();
            ListTag blockList = tag.getList(TAG_BLOCKS, Tag.TAG_COMPOUND);
            for (int i = 0; i < blockList.size(); i++) {
                blocks.add(BlueprintBlock.load(blockList.getCompound(i)));
            }
            ResourceLocation sourceDimension = tag.contains(TAG_SOURCE_DIMENSION, Tag.TAG_STRING) ?
                    ResourceLocation.tryParse(tag.getString(TAG_SOURCE_DIMENSION)) : null;
            BlockPos sourceMin = sourceDimension != null && tag.contains(TAG_SOURCE_X, Tag.TAG_INT) ?
                    new BlockPos(tag.getInt(TAG_SOURCE_X), tag.getInt(TAG_SOURCE_Y), tag.getInt(TAG_SOURCE_Z)) :
                    null;
            return new Blueprint(tag.getInt(TAG_SIZE_X), tag.getInt(TAG_SIZE_Y), tag.getInt(TAG_SIZE_Z),
                    List.copyOf(blocks), !tag.contains(TAG_CONSUMES_ITEMS, Tag.TAG_BYTE) ||
                            tag.getBoolean(TAG_CONSUMES_ITEMS),
                    sourceDimension, sourceMin);
        }

        public long volume() {
            return blocks.size();
        }

        public boolean movesSource() {
            return !consumesItems && sourceDimension != null && sourceMin != null;
        }

        public @Nullable BlockPos sourcePos(BlueprintBlock block) {
            return sourceMin == null ? null : sourceMin.offset(block.x(), block.y(), block.z());
        }
    }

    public record BlueprintBlock(int x, int y, int z, BlockState state, @Nullable CompoundTag blockEntityTag) {

        private static final String TAG_X = "x";
        private static final String TAG_Y = "y";
        private static final String TAG_Z = "z";
        private static final String TAG_STATE = "state";
        private static final String TAG_BLOCK_ENTITY = "blockEntity";

        public BlueprintBlock(int x, int y, int z, BlockState state) {
            this(x, y, z, state, null);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_X, x);
            tag.putInt(TAG_Y, y);
            tag.putInt(TAG_Z, z);
            tag.put(TAG_STATE, NbtUtils.writeBlockState(state));
            if (blockEntityTag != null) {
                tag.put(TAG_BLOCK_ENTITY, blockEntityTag.copy());
            }
            return tag;
        }

        public static BlueprintBlock load(CompoundTag tag) {
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    tag.getCompound(TAG_STATE));
            if (blockState.isAir()) {
                blockState = Blocks.AIR.defaultBlockState();
            }
            CompoundTag blockEntityTag = tag.contains(TAG_BLOCK_ENTITY, Tag.TAG_COMPOUND) ?
                    tag.getCompound(TAG_BLOCK_ENTITY).copy() : null;
            return new BlueprintBlock(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z), blockState,
                    blockEntityTag);
        }
    }

    public record MarkedPosition(ResourceLocation dimension, BlockPos pos) {

        private static final String TAG_DIMENSION = "dimension";
        private static final String TAG_X = "x";
        private static final String TAG_Y = "y";
        private static final String TAG_Z = "z";

        public static MarkedPosition load(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
            if (dimension == null) {
                dimension = Level.OVERWORLD.location();
            }
            BlockPos pos = new BlockPos(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z));
            return new MarkedPosition(dimension, pos);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_DIMENSION, dimension.toString());
            tag.putInt(TAG_X, pos.getX());
            tag.putInt(TAG_Y, pos.getY());
            tag.putInt(TAG_Z, pos.getZ());
            return tag;
        }

        public String shortText() {
            return dimension + " [" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]";
        }
    }

    public enum ToolMode {

        GEOMETRY("geometry"),
        COPYING("copying"),
        EXCHANGING("exchanging"),
        MOVING("moving"),
        CABLES("cables");

        private final String serializedName;

        ToolMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static ToolMode byName(String name) {
            for (ToolMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return GEOMETRY;
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.mode." + serializedName);
        }
    }

    public enum Shape {

        LINE("line"),
        CUBE("cube"),
        SPHERE("sphere"),
        CYLINDER("cylinder");

        private final String serializedName;

        Shape(String serializedName) {
            this.serializedName = serializedName;
        }

        public static Shape byName(String name) {
            for (Shape shape : values()) {
                if (shape.serializedName.equals(name)) {
                    return shape;
                }
            }
            return CUBE;
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.shape." + serializedName);
        }

        public String serializedName() {
            return serializedName;
        }
    }

    public enum RemoveMode {

        NONE("none"),
        REPLACEABLE("replaceable"),
        ALL("all");

        private final String serializedName;

        RemoveMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static RemoveMode byName(String name) {
            for (RemoveMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return ALL;
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.remove_mode." + serializedName);
        }
    }

    public enum BlockSelectMode {

        NONE("none"),
        CORNERS("corners"),
        EDGES("edges"),
        FACES("faces"),
        VOLUMES("volumes"),
        ALL("all");

        private final String serializedName;

        BlockSelectMode(String serializedName) {
            this.serializedName = serializedName;
        }

        private static BlockSelectMode byName(String name) {
            for (BlockSelectMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return ALL;
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.block_select_mode." + serializedName);
        }
    }

    public static class PendingAction {

        private static final String TAG_TYPE = "type";
        private static final String TAG_SELECTION = "selection";
        private static final String TAG_CURSOR = "cursor";
        private static final String TAG_REMOVED = "removed";
        private static final String TAG_SKIPPED = "skipped";
        private static final String TAG_BLOCKED = "blocked";
        private static final String TAG_OUT_OF_POWER = "outOfPower";
        private static final String TAG_TICK_COOLDOWN = "tickCooldown";
        private static final String TAG_REPLACEMENT = "replacement";
        private static final String TAG_WARNINGS = "warnings";
        private static final String TAG_ERRORS = "errors";
        private static final String TAG_PAUSED = "paused";
        private static final String TAG_BLOCK_SELECT_MODE = "blockSelectMode";
        private static final int MAX_ISSUE_POSITIONS = 64;

        private final PendingActionType type;
        private final MMSelection selection;
        private final ItemStack replacement;
        private final BlockSelectMode blockSelectMode;
        private final List<BlockPos> warnings = new java.util.ArrayList<>();
        private final List<BlockPos> errors = new java.util.ArrayList<>();
        private long cursor;
        private int removed;
        private int skipped;
        private int blocked;
        private int outOfPower;
        private int tickCooldown;
        private boolean paused;

        public PendingAction(PendingActionType type, MMSelection selection) {
            this(type, selection, ItemStack.EMPTY);
        }

        public PendingAction(PendingActionType type, MMSelection selection, ItemStack replacement) {
            this(type, selection, replacement, BlockSelectMode.ALL);
        }

        public PendingAction(PendingActionType type, MMSelection selection, ItemStack replacement,
                             BlockSelectMode blockSelectMode) {
            this.type = type;
            this.selection = selection;
            this.replacement = replacement.copy();
            this.blockSelectMode = blockSelectMode;
        }

        public static PendingAction load(CompoundTag tag) {
            PendingAction action = new PendingAction(PendingActionType.byName(tag.getString(TAG_TYPE)),
                    loadSelection(tag.getCompound(TAG_SELECTION)),
                    tag.contains(TAG_REPLACEMENT, Tag.TAG_COMPOUND) ?
                            ItemStack.of(tag.getCompound(TAG_REPLACEMENT)) : ItemStack.EMPTY,
                    BlockSelectMode.byName(tag.getString(TAG_BLOCK_SELECT_MODE)));
            action.cursor = tag.getLong(TAG_CURSOR);
            action.removed = tag.getInt(TAG_REMOVED);
            action.skipped = tag.getInt(TAG_SKIPPED);
            action.blocked = tag.getInt(TAG_BLOCKED);
            action.outOfPower = tag.getInt(TAG_OUT_OF_POWER);
            action.tickCooldown = tag.getInt(TAG_TICK_COOLDOWN);
            action.paused = tag.getBoolean(TAG_PAUSED);
            action.warnings.addAll(loadPositions(tag.getList(TAG_WARNINGS, Tag.TAG_COMPOUND)));
            action.errors.addAll(loadPositions(tag.getList(TAG_ERRORS, Tag.TAG_COMPOUND)));
            return action;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_TYPE, type.serializedName);
            tag.put(TAG_SELECTION, saveSelection(selection));
            if (!replacement.isEmpty()) {
                tag.put(TAG_REPLACEMENT, replacement.save(new CompoundTag()));
            }
            tag.putString(TAG_BLOCK_SELECT_MODE, blockSelectMode.serializedName);
            tag.putLong(TAG_CURSOR, cursor);
            tag.putInt(TAG_REMOVED, removed);
            tag.putInt(TAG_SKIPPED, skipped);
            tag.putInt(TAG_BLOCKED, blocked);
            tag.putInt(TAG_OUT_OF_POWER, outOfPower);
            tag.putInt(TAG_TICK_COOLDOWN, tickCooldown);
            tag.putBoolean(TAG_PAUSED, paused);
            tag.put(TAG_WARNINGS, savePositions(warnings));
            tag.put(TAG_ERRORS, savePositions(errors));
            return tag;
        }

        public PendingActionType type() {
            return type;
        }

        public MMSelection selection() {
            return selection;
        }

        public ItemStack replacement() {
            return replacement.copy();
        }

        public BlockSelectMode blockSelectMode() {
            return blockSelectMode;
        }

        public long cursor() {
            return cursor;
        }

        public void advanceCursor() {
            cursor++;
        }

        public int removed() {
            return removed;
        }

        public int skipped() {
            return skipped;
        }

        public int blocked() {
            return blocked;
        }

        public int outOfPower() {
            return outOfPower;
        }

        public List<BlockPos> warnings() {
            return List.copyOf(warnings);
        }

        public List<BlockPos> errors() {
            return List.copyOf(errors);
        }

        public int tickCooldown() {
            return tickCooldown;
        }

        public boolean paused() {
            return paused;
        }

        public void setTickCooldown(int tickCooldown) {
            this.tickCooldown = tickCooldown;
        }

        public void decrementTickCooldown() {
            tickCooldown--;
        }

        public void setPaused(boolean paused) {
            this.paused = paused;
        }

        public void togglePaused() {
            paused = !paused;
        }

        public void incrementRemoved() {
            removed++;
        }

        public void incrementSkipped() {
            skipped++;
        }

        public void incrementBlocked() {
            blocked++;
        }

        public void incrementOutOfPower() {
            outOfPower++;
        }

        public void recordWarning(BlockPos pos) {
            addIssuePosition(warnings, pos);
        }

        public void recordError(BlockPos pos) {
            addIssuePosition(errors, pos);
        }

        public boolean isComplete() {
            return cursor >= selection.scanVolume();
        }

        public Component progressText() {
            return Component.translatable("message.matter_manipulator.pending.progress", cursor, selection.scanVolume(),
                    removed, skipped, blocked, outOfPower);
        }

        public Component resultText(@Nullable Blueprint blueprint) {
            String key = switch (type) {
                case CABLE_REMOVE -> "message.matter_manipulator.cables.finished";
                case CABLE_PLACE -> "message.matter_manipulator.cables.place.finished";
                case EXCHANGE -> "message.matter_manipulator.exchange.finished";
                case PASTE -> blueprint != null && blueprint.movesSource() ?
                        "message.matter_manipulator.move.finished" : "message.matter_manipulator.paste.finished";
                case REMOVE -> "message.matter_manipulator.remove.finished";
            };
            return Component.translatable(key, removed, skipped, blocked, outOfPower);
        }

        private static CompoundTag saveSelection(MMSelection selection) {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", selection.dimension().toString());
            tag.putInt("minX", selection.min().getX());
            tag.putInt("minY", selection.min().getY());
            tag.putInt("minZ", selection.min().getZ());
            tag.putInt("maxX", selection.max().getX());
            tag.putInt("maxY", selection.max().getY());
            tag.putInt("maxZ", selection.max().getZ());
            tag.putString("shape", selection.shape().serializedName());
            return tag;
        }

        private static ListTag savePositions(List<BlockPos> positions) {
            ListTag tag = new ListTag();
            for (BlockPos pos : positions) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("x", pos.getX());
                posTag.putInt("y", pos.getY());
                posTag.putInt("z", pos.getZ());
                tag.add(posTag);
            }
            return tag;
        }

        private static List<BlockPos> loadPositions(ListTag tag) {
            List<BlockPos> positions = new java.util.ArrayList<>();
            for (int i = 0; i < tag.size() && i < MAX_ISSUE_POSITIONS; i++) {
                CompoundTag posTag = tag.getCompound(i);
                positions.add(new BlockPos(posTag.getInt("x"), posTag.getInt("y"), posTag.getInt("z")));
            }
            return positions;
        }

        private static void addIssuePosition(List<BlockPos> positions, BlockPos pos) {
            BlockPos immutablePos = pos.immutable();
            if (positions.contains(immutablePos)) {
                return;
            }
            if (positions.size() >= MAX_ISSUE_POSITIONS) {
                positions.remove(0);
            }
            positions.add(immutablePos);
        }

        private static MMSelection loadSelection(CompoundTag tag) {
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (dimension == null) {
                dimension = Level.OVERWORLD.location();
            }
            BlockPos min = new BlockPos(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"));
            BlockPos max = new BlockPos(tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
            return new MMSelection(dimension, min, max, Shape.byName(tag.getString("shape")));
        }
    }

    public enum PendingActionType {

        REMOVE("remove"),
        CABLE_REMOVE("cable_remove"),
        CABLE_PLACE("cable_place"),
        EXCHANGE("exchange"),
        PASTE("paste");

        private final String serializedName;

        PendingActionType(String serializedName) {
            this.serializedName = serializedName;
        }

        private static PendingActionType byName(String name) {
            for (PendingActionType type : values()) {
                if (type.serializedName.equals(name)) {
                    return type;
                }
            }
            return REMOVE;
        }
    }
}
