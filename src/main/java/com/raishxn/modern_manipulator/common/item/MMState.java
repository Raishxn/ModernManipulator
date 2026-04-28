package com.raishxn.modern_manipulator.common.item;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MMState {

    public static final String TAG_NAME = "MMState";

    private static final String TAG_COORD_A = "coordA";
    private static final String TAG_COORD_B = "coordB";
    private static final String TAG_COORD_C = "coordC";
    private static final String TAG_MODE = "mode";
    private static final String TAG_SHAPE = "shape";
    private static final String TAG_INSTALLED_UPGRADES = "installedUpgrades";
    private static final String TAG_PENDING_ACTION = "pendingAction";
    private static final String TAG_BLUEPRINT = "blueprint";

    private @Nullable MarkedPosition coordA;
    private @Nullable MarkedPosition coordB;
    private @Nullable MarkedPosition coordC;
    private ToolMode mode = ToolMode.GEOMETRY;
    private Shape shape = Shape.CUBE;
    private int installedUpgrades;
    private @Nullable PendingAction pendingAction;
    private @Nullable Blueprint blueprint;

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
        state.mode = ToolMode.byName(tag.getString(TAG_MODE));
        state.shape = Shape.byName(tag.getString(TAG_SHAPE));
        state.installedUpgrades = tag.getInt(TAG_INSTALLED_UPGRADES);
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
        tag.putString(TAG_MODE, mode.serializedName);
        tag.putString(TAG_SHAPE, shape.serializedName);
        tag.putInt(TAG_INSTALLED_UPGRADES, installedUpgrades);
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

    public record Blueprint(int sizeX, int sizeY, int sizeZ, List<BlueprintBlock> blocks, boolean consumesItems) {

        private static final String TAG_SIZE_X = "sizeX";
        private static final String TAG_SIZE_Y = "sizeY";
        private static final String TAG_SIZE_Z = "sizeZ";
        private static final String TAG_BLOCKS = "blocks";
        private static final String TAG_CONSUMES_ITEMS = "consumesItems";

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_SIZE_X, sizeX);
            tag.putInt(TAG_SIZE_Y, sizeY);
            tag.putInt(TAG_SIZE_Z, sizeZ);
            tag.putBoolean(TAG_CONSUMES_ITEMS, consumesItems);
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
            return new Blueprint(tag.getInt(TAG_SIZE_X), tag.getInt(TAG_SIZE_Y), tag.getInt(TAG_SIZE_Z),
                    List.copyOf(blocks), !tag.contains(TAG_CONSUMES_ITEMS, Tag.TAG_BYTE) ||
                            tag.getBoolean(TAG_CONSUMES_ITEMS));
        }

        public long volume() {
            return blocks.size();
        }
    }

    public record BlueprintBlock(int x, int y, int z, BlockState state) {

        private static final String TAG_X = "x";
        private static final String TAG_Y = "y";
        private static final String TAG_Z = "z";
        private static final String TAG_STATE = "state";

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_X, x);
            tag.putInt(TAG_Y, y);
            tag.putInt(TAG_Z, z);
            tag.put(TAG_STATE, NbtUtils.writeBlockState(state));
            return tag;
        }

        public static BlueprintBlock load(CompoundTag tag) {
            BlockState blockState = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),
                    tag.getCompound(TAG_STATE));
            if (blockState.isAir()) {
                blockState = Blocks.AIR.defaultBlockState();
            }
            return new BlueprintBlock(tag.getInt(TAG_X), tag.getInt(TAG_Y), tag.getInt(TAG_Z), blockState);
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

        private final PendingActionType type;
        private final MMSelection selection;
        private final ItemStack replacement;
        private long cursor;
        private int removed;
        private int skipped;
        private int blocked;
        private int outOfPower;
        private int tickCooldown;

        public PendingAction(PendingActionType type, MMSelection selection) {
            this(type, selection, ItemStack.EMPTY);
        }

        public PendingAction(PendingActionType type, MMSelection selection, ItemStack replacement) {
            this.type = type;
            this.selection = selection;
            this.replacement = replacement.copy();
        }

        public static PendingAction load(CompoundTag tag) {
            PendingAction action = new PendingAction(PendingActionType.byName(tag.getString(TAG_TYPE)),
                    loadSelection(tag.getCompound(TAG_SELECTION)),
                    tag.contains(TAG_REPLACEMENT, Tag.TAG_COMPOUND) ?
                            ItemStack.of(tag.getCompound(TAG_REPLACEMENT)) : ItemStack.EMPTY);
            action.cursor = tag.getLong(TAG_CURSOR);
            action.removed = tag.getInt(TAG_REMOVED);
            action.skipped = tag.getInt(TAG_SKIPPED);
            action.blocked = tag.getInt(TAG_BLOCKED);
            action.outOfPower = tag.getInt(TAG_OUT_OF_POWER);
            action.tickCooldown = tag.getInt(TAG_TICK_COOLDOWN);
            return action;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_TYPE, type.serializedName);
            tag.put(TAG_SELECTION, saveSelection(selection));
            if (!replacement.isEmpty()) {
                tag.put(TAG_REPLACEMENT, replacement.save(new CompoundTag()));
            }
            tag.putLong(TAG_CURSOR, cursor);
            tag.putInt(TAG_REMOVED, removed);
            tag.putInt(TAG_SKIPPED, skipped);
            tag.putInt(TAG_BLOCKED, blocked);
            tag.putInt(TAG_OUT_OF_POWER, outOfPower);
            tag.putInt(TAG_TICK_COOLDOWN, tickCooldown);
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

        public int tickCooldown() {
            return tickCooldown;
        }

        public void setTickCooldown(int tickCooldown) {
            this.tickCooldown = tickCooldown;
        }

        public void decrementTickCooldown() {
            tickCooldown--;
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

        public boolean isComplete() {
            return cursor >= selection.scanVolume();
        }

        public Component progressText() {
            return Component.translatable("message.matter_manipulator.pending.progress", cursor, selection.scanVolume(),
                    removed, skipped, blocked, outOfPower);
        }

        public Component resultText() {
            String key = switch (type) {
                case EXCHANGE -> "message.matter_manipulator.exchange.finished";
                case PASTE -> "message.matter_manipulator.paste.finished";
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
