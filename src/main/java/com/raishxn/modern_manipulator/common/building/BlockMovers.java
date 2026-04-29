package com.raishxn.modern_manipulator.common.building;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputFluid;
import com.gregtechceu.gtceu.api.machine.feature.IAutoOutputItem;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.pipenet.longdistance.LongDistancePipeBlock;
import com.gregtechceu.gtceu.common.item.IntCircuitBehaviour;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import com.raishxn.modern_manipulator.common.config.MMConfig;
import com.raishxn.modern_manipulator.common.item.MMSelection;
import com.raishxn.modern_manipulator.common.item.MMState.BlueprintBlock;
import com.raishxn.modern_manipulator.common.item.MMState.RemoveMode;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlockMovers {

    private static final String TAG_GT_CONFIG = "MMGTConfig";
    private static final String TAG_GT_KIND = "kind";
    private static final String TAG_GT_KIND_MACHINE = "machine";
    private static final String TAG_GT_KIND_PIPE = "pipe";
    private static final String TAG_REQUIRED_ITEMS = "MMRequiredItems";
    private static final String TAG_PIPE_CONNECTIONS = "pipe_connections";
    private static final String TAG_PIPE_BLOCKED_CONNECTIONS = "pipe_blocked_connections";
    private static final String TAG_COVER = "cover";
    private static final String TAG_FACING_DIR = "front_facing";
    private static final String TAG_ITEM_OUTPUT_SIDE = "output_direction_item";
    private static final String TAG_ITEM_AUTO_OUTPUT = "item_auto_output";
    private static final String TAG_ALLOW_ITEM_IN_FROM_OUT = "allow_input_from_output_item";
    private static final String TAG_FLUID_OUTPUT_SIDE = "output_direction_fluid";
    private static final String TAG_FLUID_AUTO_OUTPUT = "fluid_auto_output";
    private static final String TAG_ALLOW_FLUID_IN_FROM_OUT = "allow_input_from_output_fluid";
    private static final String TAG_MUFFLED = "muffled";
    private static final String TAG_CIRCUIT = "circuit_config";

    private static final Set<String> UNSAFE_BLOCK_ENTITY_KEYS = Set.of("forgecaps", "items", "inventory", "item",
            "fluid", "tank", "energy", "loot_table", "loottable", "command", "spawn_data", "spawnpotentials");

    private BlockMovers() {}

    public static CopyResult copyBlock(Level level, MMSelection selection, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return CopyResult.skipped();
        }

        BlockState blockState = level.getBlockState(pos);
        if (blockState.isAir()) {
            return CopyResult.skipped();
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag blockEntityTag = null;
        if (blockEntity != null) {
            blockEntityTag = safeConfigTag(blockEntity);
            if (blockEntityTag == null) {
                return CopyResult.unsafe(pos);
            }
        }

        return CopyResult.copied(new BlueprintBlock(pos.getX() - selection.min().getX(),
                pos.getY() - selection.min().getY(), pos.getZ() - selection.min().getZ(), blockState,
                blockEntityTag));
    }

    public static PasteResult pasteBlock(Level level, Player player, BlockPos pos, BlueprintBlock blueprintBlock,
                                         RemoveMode removeMode) {
        if (!canPasteBlock(level, pos, blueprintBlock, removeMode)) {
            return PasteResult.BLOCKED;
        }

        BlockState existingState = level.getBlockState(pos);
        if (!existingState.isAir()) {
            level.destroyBlock(pos, false, player);
        }
        if (!level.setBlock(pos, blueprintBlock.state(), 3)) {
            return PasteResult.BLOCKED;
        }
        if (blueprintBlock.blockEntityTag() != null &&
                !applyBlockEntityTag(level, player, pos, blueprintBlock.blockEntityTag())) {
            level.removeBlock(pos, false);
            return PasteResult.BLOCKED;
        }
        return PasteResult.PLACED;
    }

    public static PasteResult pasteCableBlock(Level level, Player player, BlockPos pos, BlueprintBlock blueprintBlock,
                                              RemoveMode removeMode) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return PasteResult.BLOCKED;
        }
        if (blueprintBlock.blockEntityTag() != null && !blueprintBlock.state().hasBlockEntity()) {
            return PasteResult.BLOCKED;
        }

        BlockState existingState = level.getBlockState(pos);
        BlockEntity existingBlockEntity = level.getBlockEntity(pos);
        if (existingBlockEntity != null && !isCableLike(existingState)) {
            return PasteResult.BLOCKED;
        }
        if (!canReplace(existingState, removeMode) || (!existingState.isAir() &&
                existingState.getDestroySpeed(level, pos) < 0.0F)) {
            return PasteResult.BLOCKED;
        }

        if (!existingState.isAir()) {
            level.destroyBlock(pos, false, player);
        }
        if (!level.setBlock(pos, blueprintBlock.state(), 3)) {
            return PasteResult.BLOCKED;
        }
        if (blueprintBlock.blockEntityTag() != null &&
                !applyBlockEntityTag(level, player, pos, blueprintBlock.blockEntityTag())) {
            level.removeBlock(pos, false);
            return PasteResult.BLOCKED;
        }
        return PasteResult.PLACED;
    }

    public static boolean canPasteBlock(Level level, BlockPos pos, BlueprintBlock blueprintBlock,
                                        RemoveMode removeMode) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.getBlockEntity(pos) != null) {
            return false;
        }
        if (blueprintBlock.blockEntityTag() != null && !blueprintBlock.state().hasBlockEntity()) {
            return false;
        }

        BlockState existingState = level.getBlockState(pos);
        if (!canReplace(existingState, removeMode)) {
            return false;
        }
        if (!existingState.isAir() && existingState.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return true;
    }

    public static boolean canMoveInto(Level level, BlockPos pos, RemoveMode removeMode) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockState existingState = level.getBlockState(pos);
        if (!canReplace(existingState, removeMode) || (!existingState.isAir() &&
                existingState.getDestroySpeed(level, pos) < 0.0F)) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null || safeConfigTag(blockEntity) != null;
    }

    public static boolean sourceStillMatches(Level level, BlockPos pos, BlueprintBlock blueprintBlock) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        if (!level.getBlockState(pos).equals(blueprintBlock.state())) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return blueprintBlock.blockEntityTag() == null;
        }
        CompoundTag currentTag = safeConfigTag(blockEntity);
        return currentTag != null && currentTag.equals(blueprintBlock.blockEntityTag());
    }

    private static boolean canReplace(BlockState existingState, RemoveMode removeMode) {
        if (existingState.isAir()) {
            return true;
        }
        return switch (removeMode) {
            case NONE -> false;
            case REPLACEABLE -> existingState.canBeReplaced();
            case ALL -> true;
        };
    }

    public static boolean isCableLike(BlockState state) {
        return state.getBlock() instanceof PipeBlock<?, ?, ?> ||
                state.getBlock() instanceof LongDistancePipeBlock ||
                state.getBlock().getClass().getSimpleName().contains("Cable");
    }

    public static @Nullable CompoundTag copyCableConfig(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState blockState = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!isCableLike(blockState) || blockEntity == null) {
            return null;
        }
        return safeConfigTag(blockEntity);
    }

    public static @Nullable CompoundTag copyExchangeConfig(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        CompoundTag configTag = safeConfigTag(blockEntity);
        if (configTag == null || !additionalRequiredItems(configTag).isEmpty()) {
            return null;
        }
        return configTag;
    }

    public static boolean applyConfigTag(Level level, Player player, BlockPos pos, CompoundTag sourceTag) {
        return applyBlockEntityTag(level, player, pos, sourceTag);
    }

    public static ListTag additionalRequiredItems(@Nullable CompoundTag blockEntityTag) {
        if (blockEntityTag == null || !blockEntityTag.contains(TAG_REQUIRED_ITEMS, Tag.TAG_LIST)) {
            return new ListTag();
        }
        return blockEntityTag.getList(TAG_REQUIRED_ITEMS, Tag.TAG_COMPOUND).copy();
    }

    public static boolean consumeAdditionalRequiredItems(Player player, @Nullable CompoundTag blockEntityTag) {
        if (player.isCreative()) {
            return true;
        }
        ListTag requiredItems = additionalRequiredItems(blockEntityTag);
        if (requiredItems.isEmpty()) {
            return true;
        }

        List<ItemStack> requiredStacks = mergeRequiredItems(requiredItems);
        for (ItemStack requiredStack : requiredStacks) {
            if (countMatchingItems(player, requiredStack) < requiredStack.getCount()) {
                return false;
            }
        }
        for (ItemStack requiredStack : requiredStacks) {
            consumeItem(player, requiredStack);
        }
        return true;
    }

    public static @Nullable CompoundTag transformBlockEntityTag(@Nullable CompoundTag sourceTag, boolean mirrorX,
                                                                boolean mirrorY, boolean mirrorZ, int rotationY) {
        if (sourceTag == null) {
            return null;
        }
        CompoundTag transformedTag = sourceTag.copy();
        if (!transformedTag.contains(TAG_GT_CONFIG, Tag.TAG_COMPOUND)) {
            return transformedTag;
        }

        CompoundTag gtConfig = transformedTag.getCompound(TAG_GT_CONFIG);
        transformDirectionName(gtConfig, TAG_FACING_DIR, mirrorX, mirrorY, mirrorZ, rotationY);
        transformDirectionName(gtConfig, TAG_ITEM_OUTPUT_SIDE, mirrorX, mirrorY, mirrorZ, rotationY);
        transformDirectionName(gtConfig, TAG_FLUID_OUTPUT_SIDE, mirrorX, mirrorY, mirrorZ, rotationY);
        if (gtConfig.contains(TAG_PIPE_CONNECTIONS, Tag.TAG_INT)) {
            gtConfig.putInt(TAG_PIPE_CONNECTIONS,
                    transformDirectionMask(gtConfig.getInt(TAG_PIPE_CONNECTIONS), mirrorX, mirrorY, mirrorZ,
                            rotationY));
        }
        if (gtConfig.contains(TAG_PIPE_BLOCKED_CONNECTIONS, Tag.TAG_INT)) {
            gtConfig.putInt(TAG_PIPE_BLOCKED_CONNECTIONS,
                    transformDirectionMask(gtConfig.getInt(TAG_PIPE_BLOCKED_CONNECTIONS), mirrorX, mirrorY, mirrorZ,
                            rotationY));
        }
        if (gtConfig.contains(TAG_COVER, Tag.TAG_COMPOUND)) {
            gtConfig.put(TAG_COVER, transformCoverConfig(gtConfig.getCompound(TAG_COVER), mirrorX, mirrorY, mirrorZ,
                    rotationY));
        }
        return transformedTag;
    }

    private static List<ItemStack> mergeRequiredItems(ListTag requiredItems) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag tag : requiredItems) {
            if (tag instanceof CompoundTag compound) {
                ItemStack requiredStack = ItemStack.of(compound);
                if (!requiredStack.isEmpty()) {
                    mergeStack(stacks, requiredStack);
                }
            }
        }
        return stacks;
    }

    private static void mergeStack(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack existingStack : stacks) {
            if (ItemStack.isSameItemSameTags(existingStack, stack)) {
                existingStack.grow(stack.getCount());
                return;
            }
        }
        stacks.add(stack.copy());
    }

    private static void transformDirectionName(CompoundTag tag, String key, boolean mirrorX, boolean mirrorY,
                                               boolean mirrorZ, int rotationY) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return;
        }
        Direction direction = Direction.byName(tag.getString(key));
        Direction transformed = transformDirection(direction, mirrorX, mirrorY, mirrorZ, rotationY);
        if (transformed != null) {
            tag.putString(key, transformed.getName());
        }
    }

    private static int transformDirectionMask(int mask, boolean mirrorX, boolean mirrorY, boolean mirrorZ,
                                              int rotationY) {
        int transformedMask = 0;
        for (Direction direction : Direction.values()) {
            if ((mask & (1 << direction.ordinal())) == 0) {
                continue;
            }
            Direction transformed = transformDirection(direction, mirrorX, mirrorY, mirrorZ, rotationY);
            if (transformed != null) {
                transformedMask |= 1 << transformed.ordinal();
            }
        }
        return transformedMask;
    }

    private static CompoundTag transformCoverConfig(CompoundTag coverTag, boolean mirrorX, boolean mirrorY,
                                                    boolean mirrorZ, int rotationY) {
        CompoundTag transformedTag = new CompoundTag();
        for (Direction direction : Direction.values()) {
            Direction transformed = transformDirection(direction, mirrorX, mirrorY, mirrorZ, rotationY);
            if (transformed != null) {
                transformedTag.put(transformed.getName(), coverTag.getCompound(direction.getName()).copy());
            }
        }
        return transformedTag;
    }

    private static @Nullable Direction transformDirection(@Nullable Direction direction, boolean mirrorX,
                                                          boolean mirrorY, boolean mirrorZ, int rotationY) {
        if (direction == null) {
            return direction;
        }
        Direction transformed = direction;
        if (mirrorY && transformed.getAxis() == Direction.Axis.Y) {
            transformed = transformed.getOpposite();
        }
        if (transformed.getAxis() == Direction.Axis.Y) {
            return transformed;
        }
        if (mirrorX && transformed.getAxis() == Direction.Axis.X) {
            transformed = transformed.getOpposite();
        }
        if (mirrorZ && transformed.getAxis() == Direction.Axis.Z) {
            transformed = transformed.getOpposite();
        }
        return switch (Math.floorMod(rotationY, 4)) {
            case 1 -> transformed.getClockWise();
            case 2 -> transformed.getOpposite();
            case 3 -> transformed.getCounterClockWise();
            default -> transformed;
        };
    }

    private static @Nullable CompoundTag safeConfigTag(BlockEntity blockEntity) {
        if (MMConfig.COPY_GTCEU_CONFIGS.get() && blockEntity instanceof IMachineBlockEntity machineBlockEntity) {
            return gregTechMachineConfig(machineBlockEntity.getMetaMachine());
        }
        if (MMConfig.COPY_GTCEU_CONFIGS.get() && blockEntity instanceof PipeBlockEntity<?, ?> pipeBlockEntity) {
            return gregTechPipeConfig(pipeBlockEntity);
        }
        return safeBlockEntityTag(blockEntity);
    }

    private static @Nullable CompoundTag safeBlockEntityTag(BlockEntity blockEntity) {
        if (!MMConfig.COPY_SAFE_BLOCK_ENTITY_NBT.get()) {
            return null;
        }
        if (hasSensitiveCapability(blockEntity)) {
            return null;
        }
        CompoundTag tag = blockEntity.saveWithFullMetadata();
        if (containsUnsafeBlockEntityData(tag)) {
            return null;
        }
        return tag;
    }

    private static boolean applyBlockEntityTag(Level level, Player player, BlockPos pos, CompoundTag sourceTag) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        if (sourceTag.contains(TAG_GT_CONFIG, Tag.TAG_COMPOUND)) {
            if (!MMConfig.COPY_GTCEU_CONFIGS.get()) {
                return false;
            }
            return applyGregTechConfig(blockEntity, player, sourceTag.getCompound(TAG_GT_CONFIG));
        }
        if (!MMConfig.COPY_SAFE_BLOCK_ENTITY_NBT.get()) {
            return false;
        }
        CompoundTag targetTag = sourceTag.copy();
        targetTag.putInt("x", pos.getX());
        targetTag.putInt("y", pos.getY());
        targetTag.putInt("z", pos.getZ());
        blockEntity.load(targetTag);
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
        return true;
    }

    private static CompoundTag gregTechMachineConfig(MetaMachine machine) {
        CompoundTag wrapper = gregTechWrapper(TAG_GT_KIND_MACHINE, machine.getItemsRequiredToPaste());
        CompoundTag tag = wrapper.getCompound(TAG_GT_CONFIG);
        tag.putString(TAG_FACING_DIR, machine.getFrontFacing().getName());

        if (machine instanceof IAutoOutputItem autoOutputItem && autoOutputItem.getOutputFacingItems() != null) {
            tag.putString(TAG_ITEM_OUTPUT_SIDE, autoOutputItem.getOutputFacingItems().getName());
            tag.putBoolean(TAG_ITEM_AUTO_OUTPUT, autoOutputItem.isAutoOutputItems());
            tag.putBoolean(TAG_ALLOW_ITEM_IN_FROM_OUT, autoOutputItem.isAllowInputFromOutputSideItems());
        }
        if (machine instanceof IAutoOutputFluid autoOutputFluid && autoOutputFluid.getOutputFacingFluids() != null) {
            tag.putString(TAG_FLUID_OUTPUT_SIDE, autoOutputFluid.getOutputFacingFluids().getName());
            tag.putBoolean(TAG_FLUID_AUTO_OUTPUT, autoOutputFluid.isAutoOutputFluids());
            tag.putBoolean(TAG_ALLOW_FLUID_IN_FROM_OUT, autoOutputFluid.isAllowInputFromOutputSideFluids());
        }
        if (machine instanceof IMufflableMachine mufflableMachine) {
            tag.putBoolean(TAG_MUFFLED, mufflableMachine.isMuffled());
        }
        if (machine instanceof IHasCircuitSlot circuitMachine) {
            int circuit = IntCircuitBehaviour
                    .getCircuitConfiguration(circuitMachine.getCircuitInventory().getStackInSlot(0));
            if (circuitMachine.isCircuitSlotEnabled() && circuit != 0) {
                tag.putInt(TAG_CIRCUIT, circuit);
            }
        }

        tag.put(TAG_COVER, machine.getCoverContainer().copyConfig(new CompoundTag()));
        machine.copyConfig(tag);
        return wrapper;
    }

    private static CompoundTag gregTechPipeConfig(PipeBlockEntity<?, ?> pipeBlockEntity) {
        CompoundTag wrapper = gregTechWrapper(TAG_GT_KIND_PIPE, pipeBlockEntity.getItemsRequiredToPaste());
        CompoundTag tag = wrapper.getCompound(TAG_GT_CONFIG);
        tag.putInt(TAG_PIPE_CONNECTIONS, pipeBlockEntity.getConnections());
        tag.putInt(TAG_PIPE_BLOCKED_CONNECTIONS, pipeBlockEntity.getBlockedConnections());
        tag.put(TAG_COVER, pipeBlockEntity.getCoverContainer().copyConfig(new CompoundTag()));
        return wrapper;
    }

    private static CompoundTag gregTechWrapper(String kind, Iterable<ItemStack> requiredItems) {
        CompoundTag wrapper = new CompoundTag();
        CompoundTag config = new CompoundTag();
        config.putString(TAG_GT_KIND, kind);
        wrapper.put(TAG_GT_CONFIG, config);
        ListTag items = new ListTag();
        for (ItemStack itemStack : requiredItems) {
            if (!itemStack.isEmpty()) {
                items.add(itemStack.save(new CompoundTag()));
            }
        }
        wrapper.put(TAG_REQUIRED_ITEMS, items);
        return wrapper;
    }

    private static boolean applyGregTechConfig(BlockEntity blockEntity, Player player, CompoundTag tag) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        String kind = tag.getString(TAG_GT_KIND);
        if (TAG_GT_KIND_MACHINE.equals(kind) && blockEntity instanceof IMachineBlockEntity machineBlockEntity) {
            pasteMachineConfig(serverPlayer, machineBlockEntity.getMetaMachine(), tag);
            return true;
        }
        if (TAG_GT_KIND_PIPE.equals(kind) && blockEntity instanceof PipeBlockEntity<?, ?> pipeBlockEntity) {
            pastePipeConfig(serverPlayer, pipeBlockEntity, tag);
            return true;
        }
        return false;
    }

    private static void pasteMachineConfig(ServerPlayer player, MetaMachine machine, CompoundTag tag) {
        if (machine instanceof IAutoOutputItem autoOutputItem) {
            if (tag.contains(TAG_ITEM_OUTPUT_SIDE)) {
                autoOutputItem.setOutputFacingItems(Direction.byName(tag.getString(TAG_ITEM_OUTPUT_SIDE)));
            }
            if (tag.contains(TAG_ITEM_AUTO_OUTPUT)) {
                autoOutputItem.setAutoOutputItems(tag.getBoolean(TAG_ITEM_AUTO_OUTPUT));
            }
            if (tag.contains(TAG_ALLOW_ITEM_IN_FROM_OUT)) {
                autoOutputItem.setAllowInputFromOutputSideItems(tag.getBoolean(TAG_ALLOW_ITEM_IN_FROM_OUT));
            }
        }
        if (machine instanceof IAutoOutputFluid autoOutputFluid) {
            if (tag.contains(TAG_FLUID_OUTPUT_SIDE)) {
                autoOutputFluid.setOutputFacingFluids(Direction.byName(tag.getString(TAG_FLUID_OUTPUT_SIDE)));
            }
            if (tag.contains(TAG_FLUID_AUTO_OUTPUT)) {
                autoOutputFluid.setAutoOutputFluids(tag.getBoolean(TAG_FLUID_AUTO_OUTPUT));
            }
            if (tag.contains(TAG_ALLOW_FLUID_IN_FROM_OUT)) {
                autoOutputFluid.setAllowInputFromOutputSideFluids(tag.getBoolean(TAG_ALLOW_FLUID_IN_FROM_OUT));
            }
        }
        Direction facing = Direction.byName(tag.getString(TAG_FACING_DIR));
        if (facing != null) {
            machine.setFrontFacing(facing);
        }
        if (machine instanceof IMufflableMachine mufflableMachine && tag.contains(TAG_MUFFLED)) {
            mufflableMachine.setMuffled(tag.getBoolean(TAG_MUFFLED));
        }
        if (machine instanceof IHasCircuitSlot circuitMachine && tag.contains(TAG_CIRCUIT)) {
            circuitMachine.getCircuitInventory().setStackInSlot(0, IntCircuitBehaviour.stack(tag.getInt(TAG_CIRCUIT)));
        }
        machine.getCoverContainer().pasteConfig(player, tag.getCompound(TAG_COVER));
        machine.pasteConfig(player, tag);
    }

    private static void pastePipeConfig(ServerPlayer player, PipeBlockEntity<?, ?> pipeBlockEntity, CompoundTag tag) {
        if (tag.contains(TAG_PIPE_CONNECTIONS)) {
            int connections = tag.getInt(TAG_PIPE_CONNECTIONS);
            for (Direction direction : GTUtil.DIRECTIONS) {
                pipeBlockEntity.setConnection(direction, PipeBlockEntity.isConnected(connections, direction), false);
            }
        }
        if (tag.contains(TAG_PIPE_BLOCKED_CONNECTIONS)) {
            int blockedConnections = tag.getInt(TAG_PIPE_BLOCKED_CONNECTIONS);
            for (Direction direction : GTUtil.DIRECTIONS) {
                pipeBlockEntity.setBlocked(direction, PipeBlockEntity.isFaceBlocked(blockedConnections, direction));
            }
        }
        pipeBlockEntity.getCoverContainer().pasteConfig(player, tag.getCompound(TAG_COVER));
    }

    private static boolean consumeItem(Player player, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return true;
        }
        if (countMatchingItems(player, wanted) < wanted.getCount()) {
            return false;
        }

        int remaining = wanted.getCount();
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(candidate, wanted)) {
                int consumed = Math.min(remaining, candidate.getCount());
                candidate.shrink(consumed);
                remaining -= consumed;
            }
        }
        return true;
    }

    private static int countMatchingItems(Player player, ItemStack wanted) {
        int available = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(candidate, wanted)) {
                available += candidate.getCount();
            }
        }
        return available;
    }

    private static boolean hasSensitiveCapability(BlockEntity blockEntity) {
        return hasCapability(blockEntity, ForgeCapabilities.ITEM_HANDLER) ||
                hasCapability(blockEntity, ForgeCapabilities.FLUID_HANDLER) ||
                hasCapability(blockEntity, ForgeCapabilities.ENERGY);
    }

    private static boolean hasCapability(BlockEntity blockEntity, Capability<?> capability) {
        if (blockEntity.getCapability(capability).isPresent()) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            if (blockEntity.getCapability(capability, direction).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnsafeBlockEntityData(CompoundTag tag) {
        for (String key : tag.getAllKeys()) {
            String normalizedKey = key.toLowerCase(Locale.ROOT);
            for (String unsafeKey : UNSAFE_BLOCK_ENTITY_KEYS) {
                if (normalizedKey.contains(unsafeKey)) {
                    return true;
                }
            }
            Tag value = tag.get(key);
            if (value instanceof CompoundTag compound && containsUnsafeBlockEntityData(compound)) {
                return true;
            }
            if (value instanceof ListTag listTag && containsUnsafeBlockEntityData(listTag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsUnsafeBlockEntityData(ListTag tag) {
        for (Tag value : tag) {
            if (value instanceof CompoundTag compound && containsUnsafeBlockEntityData(compound)) {
                return true;
            }
            if (value instanceof ListTag listTag && containsUnsafeBlockEntityData(listTag)) {
                return true;
            }
        }
        return false;
    }

    public record CopyResult(CopyResultType type, @Nullable BlueprintBlock block, @Nullable BlockPos pos) {

        private static CopyResult copied(BlueprintBlock block) {
            return new CopyResult(CopyResultType.COPIED, block, null);
        }

        private static CopyResult skipped() {
            return new CopyResult(CopyResultType.SKIPPED, null, null);
        }

        private static CopyResult unsafe(BlockPos pos) {
            return new CopyResult(CopyResultType.UNSAFE, null, pos.immutable());
        }
    }

    public enum CopyResultType {
        COPIED,
        SKIPPED,
        UNSAFE
    }

    public enum PasteResult {
        PLACED,
        BLOCKED
    }
}
