package com.raishxn.modern_manipulator.common.item;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import com.raishxn.modern_manipulator.common.item.MMState.PendingAction;
import com.raishxn.modern_manipulator.common.item.MMState.PendingActionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class MatterManipulatorItem extends Item {

    private static final double EU_PER_BLOCK = 128.0;
    private static final double BLOCK_ENTITY_PENALTY = 16.0;
    private static final double EU_DISTANCE_EXPONENT = 1.25;
    private static final int MK3_BLOCKS_PER_OPERATION = 256;

    private final ManipulatorTier tier;

    public MatterManipulatorItem(ManipulatorTier tier) {
        super(new Item.Properties().stacksTo(1).fireResistant());
        this.tier = tier;
    }

    public ManipulatorTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        MMState state = getState(stack);
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);

        tooltip.add(Component.translatable("tooltip.matter_manipulator.tier", tier.displayName())
                .withStyle(ChatFormatting.GRAY));
        if (electricItem != null) {
            ElectricStats.addCurrentChargeTooltip(tooltip, electricItem.getCharge(), electricItem.getMaxCharge(),
                    electricItem.getTier(), false);
        }
        tooltip.add(Component.translatable("tooltip.matter_manipulator.mode", state.mode().displayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.matter_manipulator.shape", state.shape().displayName())
                .withStyle(ChatFormatting.GRAY));
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_a", state.coordA());
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_b", state.coordB());
        appendSelectionTooltip(state, tooltip);
        appendPendingTooltip(state, tooltip);
        appendUpgradeTooltip(state, tooltip);
        appendCapabilityTooltip(state, tooltip);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() != null) {
            MMState state = getState(stack);
            MarkedPosition markedPosition = new MarkedPosition(level.dimension().location(), context.getClickedPos());
            boolean setCoordB = context.getPlayer().isShiftKeyDown();
            if (setCoordB) {
                state.setCoordB(markedPosition);
            } else {
                state.setCoordA(markedPosition);
            }
            setState(stack, state);
            MMSelection selection = state.selection();
            Component selectionInfo = selection == null ?
                    Component.translatable("message.matter_manipulator.selection_incomplete") :
                    Component.literal(selection.describe());
            context.getPlayer()
                    .displayClientMessage(Component.translatable(
                            setCoordB ? "message.matter_manipulator.coord_b_set" :
                                    "message.matter_manipulator.coord_a_set",
                            markedPosition.shortText(), selectionInfo), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        HitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult instanceof BlockHitResult blockHitResult && hitResult.getType() == HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        MMState state = getState(stack);
        PendingAction pendingAction = state.pendingAction();
        if (pendingAction != null) {
            state.clearPendingAction();
            setState(stack, state);
            player.displayClientMessage(Component.translatable("message.matter_manipulator.pending.cancelled"), true);
            return InteractionResultHolder.success(stack);
        }

        ActionStartResult result = switch (state.mode()) {
            case EXCHANGING -> startExchangeSelection(stack, player, level, state);
            case GEOMETRY, CABLES -> startRemoveSelection(stack, player, level, state);
            case COPYING, MOVING -> ActionStartResult.error(
                    Component.translatable("message.matter_manipulator.mode.not_implemented",
                            state.mode().displayName()));
        };
        player.displayClientMessage(result.message(), true);
        return result.started() ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !isSelected || !(entity instanceof Player player)) {
            return;
        }

        MMState state = getState(stack);
        PendingAction action = state.pendingAction();
        if (action == null) {
            return;
        }
        if (action.tickCooldown() > 0) {
            action.decrementTickCooldown();
            setState(stack, state);
            return;
        }

        ActionTickResult result = tickPendingAction(stack, player, level, state, action);
        action.setTickCooldown(Math.max(1, tier.placeTicks()) - 1);
        if (result.finished()) {
            Component message = result.message() == null ? action.resultText() : result.message();
            state.clearPendingAction();
            player.displayClientMessage(message, true);
        } else if (level.getGameTime() % 20 == 0) {
            player.displayClientMessage(action.progressText(), true);
        }
        setState(stack, state);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getCharge(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getCharge(stack) / tier.maxCharge());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float charge = (float) getCharge(stack) / (float) tier.maxCharge();
        return Mth.hsvToRgb(Math.max(0.0F, charge) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {

            private final LazyOptional<IElectricItem> electricItem = LazyOptional.of(
                    () -> new ManipulatorElectricItem(stack, tier.maxCharge(), tier.voltageTier(),
                            tier.transferLimit()));

            @Override
            public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                              @Nullable net.minecraft.core.Direction side) {
                return GTCapability.CAPABILITY_ELECTRIC_ITEM.orEmpty(capability, electricItem);
            }
        };
    }

    public static MMState getState(ItemStack stack) {
        return MMState.getOrCreate(stack.getOrCreateTag());
    }

    public static void setState(ItemStack stack, MMState state) {
        state.saveInto(stack.getOrCreateTag());
    }

    public ItemStack createChargedStack() {
        ItemStack stack = new ItemStack(this);
        charge(stack, tier.maxCharge(), true);
        return stack;
    }

    private void appendCoordTooltip(List<Component> tooltip, String translationKey, @Nullable MarkedPosition coord) {
        Component value = coord == null ? Component.translatable("tooltip.matter_manipulator.coord_unset") :
                Component.literal(coord.shortText());
        tooltip.add(Component.translatable(translationKey, value).withStyle(ChatFormatting.DARK_GRAY));
    }

    private void appendSelectionTooltip(MMState state, List<Component> tooltip) {
        MMSelection selection = state.selection();
        if (selection == null) {
            if (state.coordA() != null && state.coordB() != null) {
                tooltip.add(Component.translatable("tooltip.matter_manipulator.selection_dimension_mismatch")
                        .withStyle(ChatFormatting.RED));
            }
            return;
        }
        tooltip.add(Component.translatable("tooltip.matter_manipulator.selection", selection.describe())
                .withStyle(ChatFormatting.AQUA));
        if (tier.maxRange() >= 0) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.max_range", tier.maxRange())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.max_range_unlimited")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private void appendPendingTooltip(MMState state, List<Component> tooltip) {
        PendingAction pendingAction = state.pendingAction();
        if (pendingAction != null) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.pending", pendingAction.cursor(),
                    pendingAction.selection().volume()).withStyle(ChatFormatting.YELLOW));
        }
    }

    private void appendCapabilityTooltip(MMState state, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.matter_manipulator.capabilities").withStyle(ChatFormatting.GRAY));
        for (MMCapability capability : MMCapability.values()) {
            if (state.hasCapability(tier, capability)) {
                tooltip.add(
                        Component.literal("  ").append(capability.displayName()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    private void appendUpgradeTooltip(MMState state, List<Component> tooltip) {
        boolean hasAny = false;
        for (MMUpgrade upgrade : MMUpgrade.values()) {
            if (!state.hasUpgrade(upgrade)) {
                continue;
            }
            if (!hasAny) {
                tooltip.add(Component.translatable("tooltip.matter_manipulator.installed_upgrades")
                        .withStyle(ChatFormatting.GRAY));
                hasAny = true;
            }
            tooltip.add(Component.literal("  ").append(upgrade.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private ActionStartResult startRemoveSelection(ItemStack stack, Player player, Level level, MMState state) {
        if (!state.hasCapability(tier, MMCapability.ALLOW_REMOVING)) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.no_capability"));
        }

        MMSelection selection = state.selection();
        if (selection == null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.no_selection"));
        }
        if (!selection.dimension().equals(level.dimension().location())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.wrong_dimension"));
        }
        if (!selection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        PendingAction action = new PendingAction(PendingActionType.REMOVE, selection);
        state.startPendingAction(action);
        setState(stack, state);
        return new ActionStartResult(true, Component.translatable("message.matter_manipulator.remove.started",
                selection.describe()));
    }

    private ActionStartResult startExchangeSelection(ItemStack stack, Player player, Level level, MMState state) {
        if (!state.hasCapability(tier, MMCapability.ALLOW_EXCHANGING)) {
            return ActionStartResult.error(
                    Component.translatable("message.matter_manipulator.exchange.no_capability"));
        }

        MMSelection selection = state.selection();
        if (selection == null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.no_selection"));
        }
        if (!selection.dimension().equals(level.dimension().location())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.wrong_dimension"));
        }
        if (!selection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        ItemStack replacement = player.getOffhandItem();
        if (!(replacement.getItem() instanceof BlockItem)) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.exchange.no_block"));
        }
        if (!player.isCreative() && replacement.getCount() <= 0) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.exchange.no_items"));
        }

        PendingAction action = new PendingAction(PendingActionType.EXCHANGE, selection, replacement.copyWithCount(1));
        state.startPendingAction(action);
        setState(stack, state);
        return new ActionStartResult(true, Component.translatable("message.matter_manipulator.exchange.started",
                selection.describe(), replacement.getHoverName()));
    }

    private ActionTickResult tickPendingAction(ItemStack stack, Player player, Level level, MMState state,
                                               PendingAction action) {
        if (action.type() != PendingActionType.REMOVE && action.type() != PendingActionType.EXCHANGE) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.pending.unknown"));
        }
        if (!action.selection().dimension().equals(level.dimension().location())) {
            return ActionTickResult
                    .finished(Component.translatable("message.matter_manipulator.remove.wrong_dimension"));
        }
        if (!action.selection().isInPlayerRange(player, tier.maxRange())) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        int actionLimit = tier.placeSpeed() < 0 ? MK3_BLOCKS_PER_OPERATION : tier.placeSpeed();
        int attemptedThisTick = 0;

        while (attemptedThisTick < actionLimit && !action.isComplete()) {
            BlockPos pos = action.selection().positionAt(action.cursor());
            action.advanceCursor();
            attemptedThisTick++;
            if (!action.selection().contains(pos)) {
                continue;
            }

            if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                action.incrementSkipped();
                continue;
            }
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir()) {
                action.incrementSkipped();
                continue;
            }
            if (blockState.getDestroySpeed(level, pos) < 0.0F || !player.mayInteract(level, pos)) {
                action.incrementBlocked();
                continue;
            }

            ActionTickResult result = switch (action.type()) {
                case REMOVE -> removeBlock(stack, player, level, pos, blockState, state, action);
                case EXCHANGE -> exchangeBlock(stack, player, level, pos, blockState, state, action);
            };
            if (result.finished()) {
                return result;
            }
        }

        return action.isComplete() ? ActionTickResult.finished(action.resultText()) : ActionTickResult.running();
    }

    private ActionTickResult removeBlock(ItemStack stack, Player player, Level level, BlockPos pos,
                                         BlockState blockState, MMState state, PendingAction action) {
        long euCost = operationCost(level, player, pos, blockState, state);
        if (!consumeEnergy(stack, player, euCost)) {
            action.incrementOutOfPower();
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_power"));
        }
        if (level.destroyBlock(pos, true, player)) {
            action.incrementRemoved();
        } else {
            action.incrementBlocked();
        }
        return ActionTickResult.running();
    }

    private ActionTickResult exchangeBlock(ItemStack stack, Player player, Level level, BlockPos pos,
                                           BlockState blockState, MMState state, PendingAction action) {
        ItemStack replacement = action.replacement();
        if (!(replacement.getItem() instanceof BlockItem blockItem)) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.exchange.no_block"));
        }
        if (level.getBlockEntity(pos) != null) {
            action.incrementBlocked();
            return ActionTickResult.running();
        }

        ItemStack paymentStack = player.isCreative() ? replacement : findMatchingStack(player, replacement);
        if (!player.isCreative() && paymentStack.isEmpty()) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.exchange.no_items"));
        }

        BlockState replacementState = blockItem.getBlock().defaultBlockState();
        if (!replacementState.canSurvive(level, pos) || !level.getFluidState(pos).is(Fluids.EMPTY)) {
            action.incrementBlocked();
            return ActionTickResult.running();
        }

        long euCost = operationCost(level, player, pos, blockState, state);
        if (!consumeEnergy(stack, player, euCost)) {
            action.incrementOutOfPower();
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_power"));
        }
        if (!level.destroyBlock(pos, true, player)) {
            action.incrementBlocked();
            return ActionTickResult.running();
        }
        if (!level.setBlock(pos, replacementState, 3)) {
            action.incrementBlocked();
            return ActionTickResult.running();
        }
        if (!player.isCreative()) {
            paymentStack.shrink(1);
        }
        action.incrementRemoved();
        return ActionTickResult.running();
    }

    private ItemStack findMatchingStack(Player player, ItemStack wanted) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(candidate, wanted)) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    private long operationCost(Level level, Player player, BlockPos pos, BlockState blockState, MMState state) {
        int hardness = Mth.clamp((int) blockState.getDestroySpeed(level, pos), 0, 999);
        double euUsage = EU_PER_BLOCK * (1.0D + Math.sqrt(hardness));
        if (level.getBlockEntity(pos) != null) {
            euUsage *= BLOCK_ENTITY_PENALTY;
        }
        euUsage *= Math.pow(Math.sqrt(player.blockPosition().distSqr(pos)), EU_DISTANCE_EXPONENT);
        if (state.hasUpgrade(MMUpgrade.POWER_EFFICIENCY)) {
            euUsage *= 0.5D;
        }
        return Math.max(1L, (long) Math.ceil(euUsage));
    }

    private boolean consumeEnergy(ItemStack stack, Player player, long euCost) {
        if (player.isCreative()) {
            return true;
        }
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem == null) {
            return false;
        }
        long simulated = electricItem.discharge(euCost, Integer.MAX_VALUE, true, false, true);
        if (simulated != euCost) {
            return false;
        }
        electricItem.discharge(euCost, Integer.MAX_VALUE, true, false, false);
        return true;
    }

    private long getCharge(ItemStack stack) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        return electricItem == null ? 0L : electricItem.getCharge();
    }

    private void charge(ItemStack stack, long amount, boolean ignoreTransferLimit) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem != null) {
            electricItem.charge(amount, Integer.MAX_VALUE, ignoreTransferLimit, false);
        }
    }

    public enum ManipulatorTier {

        PROTOTYPE(
                "prototype",
                0,
                32,
                16,
                20,
                3,
                10_000_000L,
                EnumSet.of(MMCapability.ALLOW_GEOMETRY),
                EnumSet.of(MMUpgrade.MINING, MMUpgrade.SPEED, MMUpgrade.POWER_EFFICIENCY)),
        MK1(
                "mk1",
                1,
                64,
                32,
                10,
                5,
                100_000_000L,
                EnumSet.of(
                        MMCapability.ALLOW_GEOMETRY,
                        MMCapability.CONNECTS_TO_AE,
                        MMCapability.ALLOW_REMOVING,
                        MMCapability.ALLOW_EXCHANGING,
                        MMCapability.ALLOW_CONFIGURING,
                        MMCapability.ALLOW_CABLES),
                EnumSet.of(MMUpgrade.SPEED, MMUpgrade.POWER_EFFICIENCY)),
        MK2(
                "mk2",
                2,
                128,
                64,
                5,
                6,
                1_000_000_000L,
                EnumSet.of(
                        MMCapability.ALLOW_GEOMETRY,
                        MMCapability.CONNECTS_TO_AE,
                        MMCapability.ALLOW_REMOVING,
                        MMCapability.ALLOW_EXCHANGING,
                        MMCapability.ALLOW_CONFIGURING,
                        MMCapability.ALLOW_CABLES,
                        MMCapability.ALLOW_COPYING,
                        MMCapability.ALLOW_MOVING),
                EnumSet.of(MMUpgrade.SPEED, MMUpgrade.POWER_EFFICIENCY)),
        MK3(
                "mk3",
                3,
                -1,
                -1,
                5,
                7,
                10_000_000_000L,
                EnumSet.of(
                        MMCapability.ALLOW_GEOMETRY,
                        MMCapability.CONNECTS_TO_AE,
                        MMCapability.ALLOW_REMOVING,
                        MMCapability.ALLOW_EXCHANGING,
                        MMCapability.ALLOW_CONFIGURING,
                        MMCapability.ALLOW_CABLES,
                        MMCapability.ALLOW_COPYING,
                        MMCapability.ALLOW_MOVING,
                        MMCapability.CONNECTS_TO_UPLINK,
                        MMCapability.ALLOW_SMART_COPY),
                EnumSet.of(MMUpgrade.POWER_EFFICIENCY, MMUpgrade.POWER_P2P));

        private final String serializedName;
        private final int originalTier;
        private final int maxRange;
        private final int placeSpeed;
        private final int placeTicks;
        private final int voltageTier;
        private final long maxCharge;
        private final Set<MMCapability> baseCapabilities;
        private final Set<MMUpgrade> allowedUpgrades;

        ManipulatorTier(String serializedName, int originalTier, int maxRange, int placeSpeed, int placeTicks,
                        int voltageTier, long maxCharge, Set<MMCapability> baseCapabilities,
                        Set<MMUpgrade> allowedUpgrades) {
            this.serializedName = serializedName;
            this.originalTier = originalTier;
            this.maxRange = maxRange;
            this.placeSpeed = placeSpeed;
            this.placeTicks = placeTicks;
            this.voltageTier = voltageTier;
            this.maxCharge = maxCharge;
            this.baseCapabilities = baseCapabilities;
            this.allowedUpgrades = allowedUpgrades;
        }

        public String serializedName() {
            return serializedName;
        }

        public int originalTier() {
            return originalTier;
        }

        public int maxRange() {
            return maxRange;
        }

        public int placeSpeed() {
            return placeSpeed;
        }

        public int placeTicks() {
            return placeTicks;
        }

        public int voltageTier() {
            return voltageTier;
        }

        public long maxCharge() {
            return maxCharge;
        }

        public long transferLimit() {
            return GTValues.V[voltageTier] * 16L;
        }

        public boolean hasBaseCapability(MMCapability capability) {
            return baseCapabilities.contains(capability);
        }

        public Set<MMUpgrade> allowedUpgrades() {
            return allowedUpgrades;
        }

        public List<MMCapability> baseCapabilities() {
            return Arrays.asList(MMCapability.values()).stream()
                    .filter(baseCapabilities::contains)
                    .toList();
        }

        public Component displayName() {
            return Component.translatable("matter_manipulator.tier." + serializedName);
        }
    }

    private record ActionStartResult(boolean started, Component message) {

        private static ActionStartResult error(Component message) {
            return new ActionStartResult(false, message);
        }
    }

    private record ActionTickResult(boolean finished, @Nullable Component message) {

        private static ActionTickResult running() {
            return new ActionTickResult(false, null);
        }

        private static ActionTickResult finished(Component message) {
            return new ActionTickResult(true, message);
        }
    }
}
