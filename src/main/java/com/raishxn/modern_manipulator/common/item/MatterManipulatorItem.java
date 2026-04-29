package com.raishxn.modern_manipulator.common.item;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import com.raishxn.modern_manipulator.common.building.BlockMovers;
import com.raishxn.modern_manipulator.common.building.BlockMovers.CopyResult;
import com.raishxn.modern_manipulator.common.building.BlockMovers.CopyResultType;
import com.raishxn.modern_manipulator.common.building.BlockMovers.PasteResult;
import com.raishxn.modern_manipulator.common.building.BlueprintMaterials;
import com.raishxn.modern_manipulator.common.config.MMConfig;
import com.raishxn.modern_manipulator.common.integration.ae2.AE2Integration;
import com.raishxn.modern_manipulator.common.item.MMState.Blueprint;
import com.raishxn.modern_manipulator.common.item.MMState.BlueprintBlock;
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
        tooltip.add(Component.translatable("tooltip.matter_manipulator.block_select_mode",
                state.blockSelectMode().displayName()).withStyle(ChatFormatting.GRAY));
        appendExchangeTooltip(state, tooltip);
        appendCableTooltip(state, tooltip);
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_a", state.coordA());
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_b", state.coordB());
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_c", state.coordC());
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.me_downlink", state.meDownlink());
        appendBlueprintTooltip(state, tooltip);
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
            boolean setCoordB = context.getPlayer().isShiftKeyDown();
            BlockPos selectedPos = setCoordB ? context.getClickedPos() :
                    context.getClickedPos().relative(context.getClickedFace());
            MarkedPosition markedPosition = new MarkedPosition(level.dimension().location(), selectedPos);
            if (setCoordB) {
                state.setCoordB(markedPosition);
            } else if ((state.mode() == MMState.ToolMode.COPYING || state.mode() == MMState.ToolMode.MOVING) &&
                    state.selection() != null) {
                        state.setCoordC(markedPosition);
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
                                    (state.coordC() != null && markedPosition.equals(state.coordC())) ?
                                            "message.matter_manipulator.coord_c_set" :
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

        InteractionResult result = runConfiguredAction(stack, player, level);
        return result == InteractionResult.SUCCESS ? InteractionResultHolder.success(stack) :
                InteractionResultHolder.fail(stack);
    }

    public InteractionResult runConfiguredAction(ItemStack stack, Player player, Level level) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        MMState state = getState(stack);
        PendingAction pendingAction = state.pendingAction();
        if (pendingAction != null) {
            state.clearPendingAction();
            setState(stack, state);
            player.displayClientMessage(Component.translatable("message.matter_manipulator.pending.cancelled"), true);
            return InteractionResult.SUCCESS;
        }

        ActionStartResult result = switch (state.mode()) {
            case EXCHANGING -> startExchangeSelection(stack, player, level, state);
            case GEOMETRY -> startRemoveSelection(stack, player, level, state);
            case CABLES -> startCableSelection(stack, player, level, state);
            case COPYING -> startCopyOrPaste(stack, player, level, state);
            case MOVING -> startMoveOrPaste(stack, player, level, state);
        };
        player.displayClientMessage(result.message(), true);
        return result.started() ? InteractionResult.SUCCESS : InteractionResult.FAIL;
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
        if (action.paused()) {
            return;
        }
        if (action.tickCooldown() > 0) {
            action.decrementTickCooldown();
            setState(stack, state);
            return;
        }

        ActionTickResult result = tickPendingAction(stack, player, level, state, action);
        action.setTickCooldown(effectivePlaceTicks(state) - 1);
        if (result.finished()) {
            Component message = result.message() == null ? action.resultText(state.blueprint()) : result.message();
            if (action.type() == PendingActionType.PASTE) {
                if (state.blueprint() != null && !state.blueprint().consumesItems()) {
                    state.setBlueprint(null);
                }
                if (MMConfig.CLEAR_PASTE_TARGET_AFTER_PASTE.get()) {
                    state.clearCoordC();
                }
                if (MMConfig.RESET_TRANSFORM_AFTER_PASTE.get()) {
                    state.resetTransform();
                }
            }
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

    private void appendBlueprintTooltip(MMState state, List<Component> tooltip) {
        Blueprint blueprint = state.blueprint();
        if (blueprint != null) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.blueprint", blueprint.sizeX(),
                    blueprint.sizeY(), blueprint.sizeZ(), blueprint.volume()).withStyle(ChatFormatting.AQUA));
            if (state.pasteArrayCopies() > 1) {
                tooltip.add(Component.translatable("tooltip.matter_manipulator.array",
                        state.pasteArrayX(), state.pasteArrayY(), state.pasteArrayZ(),
                        blueprint.volume() * state.pasteArrayCopies()).withStyle(ChatFormatting.DARK_AQUA));
            }
            if (state.hasPasteOffset()) {
                tooltip.add(Component.translatable("tooltip.matter_manipulator.offset",
                        state.pasteOffsetX(), state.pasteOffsetY(), state.pasteOffsetZ())
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            appendRequiredItemsTooltip(blueprint, state.pasteArrayCopies(), tooltip);
        }
    }

    private void appendRequiredItemsTooltip(Blueprint blueprint, long multiplier, List<Component> tooltip) {
        List<ItemStack> requiredItems = BlueprintMaterials.requiredItems(blueprint, multiplier);
        if (requiredItems.isEmpty()) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.matter_manipulator.required_items")
                .withStyle(ChatFormatting.GRAY));
        int shown = 0;
        for (ItemStack requiredItem : requiredItems) {
            if (shown >= 8) {
                tooltip.add(Component.translatable("tooltip.matter_manipulator.required_items_more",
                        requiredItems.size() - shown).withStyle(ChatFormatting.DARK_GRAY));
                return;
            }
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("tooltip.matter_manipulator.required_item",
                            requiredItem.getHoverName(), requiredItem.getCount()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            shown++;
        }
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

    private void appendExchangeTooltip(MMState state, List<Component> tooltip) {
        if (state.exchangeReplacement().isEmpty() && !state.hasExchangeWhitelist()) {
            return;
        }
        if (!state.exchangeReplacement().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.exchange_replacement",
                    state.exchangeReplacement().getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
        }
        if (state.hasExchangeWhitelist()) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.exchange_whitelist",
                    state.exchangeWhitelist().size()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    private void appendCableTooltip(MMState state, List<Component> tooltip) {
        if (!state.cableStack().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.matter_manipulator.cable",
                    state.cableStack().getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
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
        ActionStartResult sizeResult = validateSelectionSize(selection);
        if (sizeResult != null) {
            return sizeResult;
        }
        if (!selection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        PendingAction action = new PendingAction(PendingActionType.REMOVE, selection, ItemStack.EMPTY,
                state.blockSelectMode());
        state.startPendingAction(action);
        setState(stack, state);
        return new ActionStartResult(true, Component.translatable("message.matter_manipulator.remove.started",
                selection.describe()));
    }

    private ActionStartResult startCableSelection(ItemStack stack, Player player, Level level, MMState state) {
        if (!state.hasCapability(tier, MMCapability.ALLOW_CABLES)) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.cables.no_capability"));
        }

        MMSelection selection = state.selection();
        if (selection == null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.no_selection"));
        }
        if (!selection.dimension().equals(level.dimension().location())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.wrong_dimension"));
        }
        ActionStartResult sizeResult = validateSelectionSize(selection);
        if (sizeResult != null) {
            return sizeResult;
        }
        if (!selection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        PendingAction action;
        Component message;
        if (state.cableStack().isEmpty()) {
            action = new PendingAction(PendingActionType.CABLE_REMOVE, selection, ItemStack.EMPTY,
                    state.blockSelectMode());
            message = Component.translatable("message.matter_manipulator.cables.started", selection.describe());
        } else {
            action = new PendingAction(PendingActionType.CABLE_PLACE, selection, state.cableStack(),
                    state.blockSelectMode());
            message = Component.translatable("message.matter_manipulator.cables.place.started",
                    selection.describe(), state.cableStack().getHoverName());
        }
        state.startPendingAction(action);
        setState(stack, state);
        return new ActionStartResult(true, message);
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
        ActionStartResult sizeResult = validateSelectionSize(selection);
        if (sizeResult != null) {
            return sizeResult;
        }
        if (!selection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        ItemStack replacement = state.exchangeReplacement();
        if (replacement.isEmpty() && player.getOffhandItem().getItem() instanceof BlockItem) {
            replacement = player.getOffhandItem().copyWithCount(1);
        }
        if (!(replacement.getItem() instanceof BlockItem)) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.exchange.no_block"));
        }
        if (!player.isCreative() && countAvailableItems(player, state, replacement.copyWithCount(1)) <= 0) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.exchange.no_items"));
        }
        if (!state.hasExchangeWhitelist()) {
            return ActionStartResult.error(Component.translatable(
                    "message.matter_manipulator.exchange.no_whitelist"));
        }

        PendingAction action = new PendingAction(PendingActionType.EXCHANGE, selection, replacement.copyWithCount(1),
                state.blockSelectMode());
        state.startPendingAction(action);
        setState(stack, state);
        return new ActionStartResult(true, Component.translatable("message.matter_manipulator.exchange.started",
                selection.describe(), replacement.getHoverName()));
    }

    private ActionStartResult startCopyOrPaste(ItemStack stack, Player player, Level level, MMState state) {
        if (!state.hasCapability(tier, MMCapability.ALLOW_COPYING)) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.copy.no_capability"));
        }
        if (state.blueprint() != null && state.coordC() != null) {
            return startPaste(stack, player, level, state);
        }
        return copySelection(stack, player, level, state, false);
    }

    private ActionStartResult startMoveOrPaste(ItemStack stack, Player player, Level level, MMState state) {
        if (!state.hasCapability(tier, MMCapability.ALLOW_MOVING)) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.move.no_capability"));
        }
        if (state.blueprint() != null && state.coordC() != null) {
            return startPaste(stack, player, level, state);
        }
        ActionStartResult copyResult = copySelection(stack, player, level, state, true);
        if (!copyResult.started()) {
            return copyResult;
        }
        return copyResult;
    }

    private ActionStartResult copySelection(ItemStack stack, Player player, Level level, MMState state,
                                            boolean moving) {
        MMSelection selection = state.selection();
        if (selection == null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.no_selection"));
        }
        if (!selection.dimension().equals(level.dimension().location())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.wrong_dimension"));
        }
        ActionStartResult sizeResult = validateSelectionSize(selection);
        if (sizeResult != null) {
            return sizeResult;
        }
        if (!selection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }

        List<BlueprintBlock> blocks = new java.util.ArrayList<>();
        int unsafeSkipped = 0;
        for (BlockPos pos : selection.positions()) {
            if (!selection.contains(pos, state.blockSelectMode())) {
                continue;
            }
            if (moving && !player.mayInteract(level, pos)) {
                return ActionStartResult.error(Component.translatable(
                        "message.matter_manipulator.move.protected_source",
                        pos.getX(), pos.getY(), pos.getZ()));
            }
            CopyResult copyResult = BlockMovers.copyBlock(level, selection, pos);
            if (copyResult.type() == CopyResultType.COPIED && copyResult.block() != null) {
                blocks.add(copyResult.block());
            } else if (copyResult.type() == CopyResultType.UNSAFE && moving && copyResult.pos() != null) {
                return ActionStartResult.error(Component.translatable(
                        "message.matter_manipulator.move.unsafe_block_entity",
                        copyResult.pos().getX(), copyResult.pos().getY(), copyResult.pos().getZ()));
            } else if (copyResult.type() == CopyResultType.UNSAFE) {
                unsafeSkipped++;
            }
        }
        if (blocks.isEmpty()) {
            return ActionStartResult.error(Component.translatable(moving ?
                    "message.matter_manipulator.move.no_blocks" : "message.matter_manipulator.copy.no_blocks"));
        }
        Blueprint blueprint = new Blueprint(selection.sizeX(), selection.sizeY(), selection.sizeZ(),
                List.copyOf(blocks), !moving, moving ? selection.dimension() : null, moving ? selection.min() : null);
        state.setBlueprint(blueprint);
        setState(stack, state);
        if (moving) {
            return new ActionStartResult(true, Component.translatable("message.matter_manipulator.move.copied",
                    blueprint.volume(), selection.describe()));
        }
        String messageKey = unsafeSkipped > 0 ? "message.matter_manipulator.copy.copied_with_skips" :
                "message.matter_manipulator.copy.copied";
        return new ActionStartResult(true, Component.translatable(messageKey, blueprint.volume(),
                selection.describe(), unsafeSkipped));
    }

    private ActionStartResult startPaste(ItemStack stack, Player player, Level level, MMState state) {
        Blueprint blueprint = state.blueprint();
        MarkedPosition coordC = state.coordC();
        if (blueprint == null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.paste.no_blueprint"));
        }
        if (coordC == null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.paste.no_coord_c"));
        }
        if (!coordC.dimension().equals(level.dimension().location())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.wrong_dimension"));
        }

        BlockPos min = state.pasteOrigin(coordC.pos());
        BlockPos max = min.offset(state.pasteArraySizeX(blueprint) - 1, state.pasteArraySizeY(blueprint) - 1,
                state.pasteArraySizeZ(blueprint) - 1);
        MMSelection pasteSelection = new MMSelection(level.dimension().location(), min, max, MMState.Shape.CUBE);
        ActionStartResult sizeResult = validateSelectionSize(pasteSelection);
        if (sizeResult != null) {
            return sizeResult;
        }
        if (!pasteSelection.isInPlayerRange(player, tier.maxRange())) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.remove.out_of_range",
                    tier.maxRange()));
        }
        if (blueprint.movesSource()) {
            if (state.pasteArrayCopies() > 1) {
                return ActionStartResult.error(Component.translatable("message.matter_manipulator.move.array_blocked"));
            }
            if (!level.dimension().location().equals(blueprint.sourceDimension())) {
                return ActionStartResult.error(Component.translatable(
                        "message.matter_manipulator.move.source_wrong_dimension"));
            }
            MMSelection sourceBounds = new MMSelection(blueprint.sourceDimension(), blueprint.sourceMin(),
                    blueprint.sourceMin().offset(blueprint.sizeX() - 1, blueprint.sizeY() - 1,
                            blueprint.sizeZ() - 1),
                    MMState.Shape.CUBE);
            if (selectionsIntersect(sourceBounds, pasteSelection)) {
                return ActionStartResult.error(Component.translatable(
                        "message.matter_manipulator.move.overlaps_source"));
            }
        }
        MissingItem missingItem = missingRequiredItem(player, state, blueprint, state.pasteArrayCopies());
        if (missingItem != null) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.paste.missing_items",
                    missingItem.stack().getHoverName(), missingItem.stack().getCount(), missingItem.available()));
        }
        PastePreflightResult preflightResult = validatePasteTargets(level, player, state, blueprint, pasteSelection);
        if (!preflightResult.valid()) {
            return ActionStartResult.error(preflightResult.message());
        }
        PendingAction action = new PendingAction(PendingActionType.PASTE, pasteSelection, ItemStack.EMPTY,
                MMState.BlockSelectMode.ALL, pastePositions(state, blueprint, min));
        state.startPendingAction(action);
        setState(stack, state);
        return new ActionStartResult(true, Component.translatable("message.matter_manipulator.paste.started",
                blueprint.volume() * state.pasteArrayCopies(), coordC.shortText()));
    }

    private ActionTickResult tickPendingAction(ItemStack stack, Player player, Level level, MMState state,
                                               PendingAction action) {
        if (action.type() != PendingActionType.REMOVE && action.type() != PendingActionType.CABLE_REMOVE &&
                action.type() != PendingActionType.CABLE_PLACE &&
                action.type() != PendingActionType.EXCHANGE &&
                action.type() != PendingActionType.PASTE) {
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

        int actionLimit = effectiveBlocksPerOperation(state);
        int attemptedThisTick = 0;

        while (attemptedThisTick < actionLimit && !action.isComplete()) {
            BlockPos pos = action.positionAt(action.cursor());
            action.advanceCursor();
            attemptedThisTick++;
            if (!action.selection().contains(pos)) {
                continue;
            }
            if (action.type() != PendingActionType.PASTE &&
                    !action.selection().contains(pos, action.blockSelectMode())) {
                action.incrementSkipped();
                continue;
            }

            if (!level.getWorldBorder().isWithinBounds(pos)) {
                action.incrementSkipped();
                action.recordWarning(pos);
                continue;
            }
            if (!level.isLoaded(pos)) {
                action.rewindCursor();
                action.recordWarning(pos);
                action.setPaused(true);
                player.displayClientMessage(Component.translatable(
                        "message.matter_manipulator.pending.paused_unloaded_chunk",
                        pos.getX(), pos.getY(), pos.getZ()), true);
                return ActionTickResult.running();
            }
            BlockState blockState = level.getBlockState(pos);
            if (action.type() != PendingActionType.PASTE && action.type() != PendingActionType.CABLE_PLACE &&
                    blockState.isAir()) {
                action.incrementSkipped();
                action.recordWarning(pos);
                continue;
            }
            if (action.type() != PendingActionType.PASTE && action.type() != PendingActionType.CABLE_PLACE &&
                    (blockState.getDestroySpeed(level, pos) < 0.0F || !player.mayInteract(level, pos))) {
                action.incrementBlocked();
                action.recordError(pos);
                continue;
            }
            if (action.type() == PendingActionType.CABLE_PLACE && !player.mayInteract(level, pos)) {
                action.incrementBlocked();
                action.recordError(pos);
                continue;
            }
            if (action.type() == PendingActionType.CABLE_REMOVE && !BlockMovers.isCableLike(blockState)) {
                action.incrementSkipped();
                action.recordWarning(pos);
                continue;
            }
            if (action.type() == PendingActionType.EXCHANGE && !state.isExchangeWhitelisted(blockState)) {
                action.incrementSkipped();
                continue;
            }

            ActionTickResult result = switch (action.type()) {
                case REMOVE, CABLE_REMOVE -> removeBlock(stack, player, level, pos, blockState, state, action);
                case CABLE_PLACE -> placeCable(stack, player, level, pos, state, action);
                case EXCHANGE -> exchangeBlock(stack, player, level, pos, blockState, state, action);
                case PASTE -> pasteBlock(stack, player, level, pos, state, action);
            };
            if (result.finished()) {
                return result;
            }
        }

        return action.isComplete() ? ActionTickResult.finished(action.resultText(state.blueprint())) :
                ActionTickResult.running();
    }

    private ActionTickResult removeBlock(ItemStack stack, Player player, Level level, BlockPos pos,
                                         BlockState blockState, MMState state, PendingAction action) {
        long euCost = operationCost(level, player, pos, blockState, state);
        if (!consumeEnergy(stack, player, euCost)) {
            action.incrementOutOfPower();
            action.recordError(pos);
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_power"));
        }
        if (removeBlockWithDrops(level, pos, player, state, stack)) {
            action.incrementRemoved();
        } else {
            action.incrementBlocked();
            action.recordError(pos);
        }
        return ActionTickResult.running();
    }

    private ActionTickResult placeCable(ItemStack stack, Player player, Level level, BlockPos pos,
                                        MMState state, PendingAction action) {
        ItemStack cableStack = action.replacement();
        BlockState cableState = state.cableState();
        if (!(cableStack.getItem() instanceof BlockItem blockItem)) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.cables.no_cable"));
        }
        if (cableState == null) {
            cableState = blockItem.getBlock().defaultBlockState();
        }
        if (!BlockMovers.isCableLike(cableState)) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.cables.not_cable"));
        }
        if (level.getBlockState(pos).equals(cableState)) {
            action.incrementSkipped();
            return ActionTickResult.running();
        }
        CompoundTag cableConfig = BlockMovers.copyCableConfig(level, pos);
        BlueprintBlock cableBlock = new BlueprintBlock(0, 0, 0, cableState, cableConfig);
        if (!cableState.canSurvive(level, pos) || !level.getFluidState(pos).is(Fluids.EMPTY)) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        if (!player.isCreative() && countAvailableItems(player, state, cableStack.copyWithCount(1)) <= 0) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.cables.no_items"));
        }

        long euCost = operationCost(level, player, pos, cableState, state);
        if (!consumeEnergy(stack, player, euCost)) {
            action.incrementOutOfPower();
            action.recordError(pos);
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_power"));
        }
        List<ItemStack> replacedDrops = MMConfig.DROP_REPLACED_BLOCKS_ON_PASTE.get() ?
                blockDrops(level, pos, level.getBlockState(pos), player, stack) : List.of();
        if (BlockMovers.pasteCableBlock(level, player, pos, cableBlock, state.removeMode()) == PasteResult.PLACED) {
            if (!player.isCreative() && !consumeRequiredItem(player, state, cableStack.copyWithCount(1))) {
                level.removeBlock(pos, false);
                action.incrementBlocked();
                action.recordError(pos);
                return ActionTickResult.running();
            }
            handleDrops(level, pos, player, state, replacedDrops);
            action.incrementRemoved();
        } else {
            action.incrementBlocked();
            action.recordError(pos);
        }
        return ActionTickResult.running();
    }

    private ActionTickResult exchangeBlock(ItemStack stack, Player player, Level level, BlockPos pos,
                                           BlockState blockState, MMState state, PendingAction action) {
        ItemStack replacement = action.replacement();
        if (!(replacement.getItem() instanceof BlockItem blockItem)) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.exchange.no_block"));
        }
        CompoundTag exchangeConfig = null;
        if (level.getBlockEntity(pos) != null) {
            exchangeConfig = BlockMovers.copyExchangeConfig(level, pos);
        }
        if (level.getBlockEntity(pos) != null && exchangeConfig == null) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }

        if (!player.isCreative() && countAvailableItems(player, state, replacement.copyWithCount(1)) <= 0) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.exchange.no_items"));
        }

        BlockState replacementState = state.exchangeReplacementState() == null ?
                blockItem.getBlock().defaultBlockState() : state.exchangeReplacementState();
        replacementState = copyCompatibleProperties(blockState, replacementState);
        if (exchangeConfig != null && !replacementState.hasBlockEntity()) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        if (!replacementState.canSurvive(level, pos) || !level.getFluidState(pos).is(Fluids.EMPTY)) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }

        long euCost = operationCost(level, player, pos, blockState, state);
        if (!consumeEnergy(stack, player, euCost)) {
            action.incrementOutOfPower();
            action.recordError(pos);
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_power"));
        }
        if (!player.isCreative() && !consumeRequiredItem(player, state, replacement.copyWithCount(1))) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        List<ItemStack> drops = blockDrops(level, pos, blockState, player, stack);
        if (!level.setBlock(pos, replacementState, 3)) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        if (exchangeConfig != null && !BlockMovers.applyConfigTag(level, player, pos, exchangeConfig)) {
            level.removeBlock(pos, false);
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        handleDrops(level, pos, player, state, drops);
        action.incrementRemoved();
        return ActionTickResult.running();
    }

    private ActionTickResult pasteBlock(ItemStack stack, Player player, Level level, BlockPos pos, MMState state,
                                        PendingAction action) {
        Blueprint blueprint = state.blueprint();
        if (blueprint == null) {
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.paste.no_blueprint"));
        }
        int relX = pos.getX() - action.selection().min().getX();
        int relY = pos.getY() - action.selection().min().getY();
        int relZ = pos.getZ() - action.selection().min().getZ();
        int localX = relX % state.pasteSizeX(blueprint);
        int localY = relY % blueprint.sizeY();
        int localZ = relZ % state.pasteSizeZ(blueprint);
        BlueprintBlock sourceBlock = null;
        BlueprintBlock pasteBlock = null;
        for (BlueprintBlock candidate : blueprint.blocks()) {
            BlockPos transformedRelative = state.transformedRelative(blueprint, candidate);
            if (transformedRelative.getX() == localX && transformedRelative.getY() == localY &&
                    transformedRelative.getZ() == localZ) {
                sourceBlock = candidate;
                pasteBlock = state.transformedBlock(blueprint, candidate);
                break;
            }
        }
        if (sourceBlock == null || pasteBlock == null) {
            action.incrementSkipped();
            action.recordWarning(pos);
            return ActionTickResult.running();
        }
        if (!player.mayInteract(level, pos)) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        if (!BlockMovers.canPasteBlock(level, pos, pasteBlock, state.removeMode())) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }
        if (blueprint.movesSource() && !canRemoveMovedSource(level, player, blueprint, sourceBlock)) {
            action.incrementBlocked();
            action.recordError(pos);
            return ActionTickResult.running();
        }

        if (blueprint.consumesItems() && !player.isCreative()) {
            ItemStack wanted = new ItemStack(pasteBlock.state().getBlock());
            if (wanted.isEmpty()) {
                action.incrementBlocked();
                action.recordError(pos);
                return ActionTickResult.running();
            }
            if (countAvailableItems(player, state, wanted) <= 0) {
                return ActionTickResult.finished(Component.translatable("message.matter_manipulator.paste.no_items"));
            }
        }
        long euCost = operationCost(level, player, pos, pasteBlock.state(), state);
        if (pasteBlock.blockEntityTag() != null) {
            euCost = Math.max(1L, (long) Math.ceil(euCost * BLOCK_ENTITY_PENALTY));
        }
        if (!consumeEnergy(stack, player, euCost)) {
            action.incrementOutOfPower();
            action.recordError(pos);
            return ActionTickResult.finished(Component.translatable("message.matter_manipulator.remove.out_of_power"));
        }
        List<ItemStack> replacedDrops = MMConfig.DROP_REPLACED_BLOCKS_ON_PASTE.get() ?
                blockDrops(level, pos, level.getBlockState(pos), player, stack) : List.of();
        if (BlockMovers.pasteBlock(level, player, pos, pasteBlock, state.removeMode()) == PasteResult.PLACED) {
            if (blueprint.consumesItems() && !consumeRequiredItem(player, state, new ItemStack(
                    pasteBlock.state().getBlock()))) {
                level.removeBlock(pos, false);
                action.incrementBlocked();
                action.recordError(pos);
                return ActionTickResult.running();
            }
            if (blueprint.consumesItems() && !consumeAdditionalRequiredItems(player, state,
                    pasteBlock.blockEntityTag())) {
                level.removeBlock(pos, false);
                action.incrementBlocked();
                action.recordError(pos);
                return ActionTickResult.running();
            }
            if (blueprint.movesSource() && !removeMovedSource(level, player, blueprint, sourceBlock)) {
                level.removeBlock(pos, false);
                action.incrementBlocked();
                action.recordError(pos);
                return ActionTickResult.running();
            }
            handleDrops(level, pos, player, state, replacedDrops);
            action.incrementRemoved();
        } else {
            action.incrementBlocked();
            action.recordError(pos);
        }
        return ActionTickResult.running();
    }

    private List<BlockPos> pastePositions(MMState state, Blueprint blueprint, BlockPos origin) {
        List<BlockPos> positions = new java.util.ArrayList<>();
        int strideX = state.pasteSizeX(blueprint);
        int strideY = blueprint.sizeY();
        int strideZ = state.pasteSizeZ(blueprint);
        for (int arrayY = 0; arrayY < state.pasteArrayY(); arrayY++) {
            for (int arrayZ = 0; arrayZ < state.pasteArrayZ(); arrayZ++) {
                for (int arrayX = 0; arrayX < state.pasteArrayX(); arrayX++) {
                    BlockPos arrayOrigin = origin.offset(arrayX * strideX, arrayY * strideY, arrayZ * strideZ);
                    for (BlueprintBlock sourceBlock : blueprint.blocks()) {
                        BlueprintBlock pasteBlock = state.transformedBlock(blueprint, sourceBlock);
                        positions.add(arrayOrigin.offset(pasteBlock.x(), pasteBlock.y(), pasteBlock.z()));
                    }
                }
            }
        }
        return positions;
    }

    private boolean removeBlockWithDrops(Level level, BlockPos pos, Player player, MMState state,
                                         ItemStack toolStack) {
        if (!(level instanceof ServerLevel)) {
            return level.destroyBlock(pos, true, player);
        }
        List<ItemStack> drops = blockDrops(level, pos, level.getBlockState(pos), player, toolStack);
        if (!level.destroyBlock(pos, false, player)) {
            return false;
        }
        handleDrops(level, pos, player, state, drops);
        return true;
    }

    private List<ItemStack> blockDrops(Level level, BlockPos pos, BlockState blockState, Player player,
                                       ItemStack toolStack) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return Block.getDrops(blockState, serverLevel, pos, blockEntity, player, toolStack);
    }

    private void handleDrops(Level level, BlockPos pos, Player player, MMState state, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            ItemStack remainder = drop;
            if (player instanceof ServerPlayer serverPlayer && AE2Integration.isLinked(serverPlayer, state)) {
                remainder = AE2Integration.insertItem(serverPlayer, state, drop);
            }
            if (!remainder.isEmpty()) {
                Block.popResource(level, pos, remainder);
            }
        }
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

    private @Nullable MissingItem missingRequiredItem(Player player, MMState state, Blueprint blueprint,
                                                      long multiplier) {
        if (!blueprint.consumesItems() || player.isCreative()) {
            return null;
        }
        for (ItemStack requiredItem : BlueprintMaterials.requiredItems(blueprint, multiplier)) {
            int available = countAvailableItems(player, state, requiredItem);
            if (available < requiredItem.getCount()) {
                return new MissingItem(requiredItem, available);
            }
        }
        return null;
    }

    private PastePreflightResult validatePasteTargets(Level level, Player player, MMState state, Blueprint blueprint,
                                                      MMSelection pasteSelection) {
        int strideX = state.pasteSizeX(blueprint);
        int strideY = blueprint.sizeY();
        int strideZ = state.pasteSizeZ(blueprint);
        for (int arrayY = 0; arrayY < state.pasteArrayY(); arrayY++) {
            for (int arrayZ = 0; arrayZ < state.pasteArrayZ(); arrayZ++) {
                for (int arrayX = 0; arrayX < state.pasteArrayX(); arrayX++) {
                    BlockPos arrayOffset = new BlockPos(arrayX * strideX, arrayY * strideY, arrayZ * strideZ);
                    for (BlueprintBlock sourceBlock : blueprint.blocks()) {
                        BlueprintBlock pasteBlock = state.transformedBlock(blueprint, sourceBlock);
                        BlockPos pos = pasteSelection.min().offset(arrayOffset)
                                .offset(pasteBlock.x(), pasteBlock.y(), pasteBlock.z());
                        if (!player.mayInteract(level, pos) || !BlockMovers.canPasteBlock(level, pos, pasteBlock,
                                state.removeMode())) {
                            return PastePreflightResult.blocked(Component.translatable(
                                    "message.matter_manipulator.paste.target_blocked",
                                    pos.getX(), pos.getY(), pos.getZ()));
                        }
                        if (blueprint.movesSource() && !canRemoveMovedSource(level, player, blueprint, sourceBlock)) {
                            BlockPos sourcePos = blueprint.sourcePos(sourceBlock);
                            return PastePreflightResult.blocked(Component.translatable(
                                    "message.matter_manipulator.move.source_blocked",
                                    sourcePos == null ? 0 : sourcePos.getX(),
                                    sourcePos == null ? 0 : sourcePos.getY(),
                                    sourcePos == null ? 0 : sourcePos.getZ()));
                        }
                    }
                }
            }
        }
        return PastePreflightResult.VALID;
    }

    private int countMatchingItems(Player player, ItemStack wanted) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(candidate, wanted)) {
                count += candidate.getCount();
            }
        }
        return count;
    }

    private int countAvailableItems(Player player, MMState state, ItemStack wanted) {
        long available = countMatchingItems(player, wanted);
        if (player instanceof ServerPlayer serverPlayer) {
            available += AE2Integration.countItem(serverPlayer, state, wanted);
        }
        return available > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) available;
    }

    private boolean consumeRequiredItem(Player player, MMState state, ItemStack wanted) {
        if (player.isCreative() || wanted.isEmpty()) {
            return true;
        }
        if (countAvailableItems(player, state, wanted) < wanted.getCount()) {
            return false;
        }

        int inventoryCount = countMatchingItems(player, wanted);
        int networkCount = Math.max(0, wanted.getCount() - inventoryCount);
        if (networkCount > 0 && (!(player instanceof ServerPlayer serverPlayer) ||
                !AE2Integration.extractItem(serverPlayer, state, wanted.copyWithCount(networkCount), networkCount))) {
            return false;
        }

        int remaining = wanted.getCount() - networkCount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameTags(candidate, wanted)) {
                int consumed = Math.min(remaining, candidate.getCount());
                candidate.shrink(consumed);
                remaining -= consumed;
            }
        }
        if (remaining <= 0) {
            return true;
        }
        return false;
    }

    private boolean consumeAdditionalRequiredItems(Player player, MMState state, @Nullable CompoundTag blockEntityTag) {
        ListTag requiredItems = BlockMovers.additionalRequiredItems(blockEntityTag);
        if (player.isCreative() || requiredItems.isEmpty()) {
            return true;
        }
        List<ItemStack> requiredStacks = new java.util.ArrayList<>();
        for (Tag tag : requiredItems) {
            if (tag instanceof CompoundTag compound) {
                ItemStack requiredStack = ItemStack.of(compound);
                if (!requiredStack.isEmpty()) {
                    mergeRequiredStack(requiredStacks, requiredStack);
                }
            }
        }
        for (ItemStack requiredStack : requiredStacks) {
            if (countAvailableItems(player, state, requiredStack) < requiredStack.getCount()) {
                return false;
            }
        }
        for (ItemStack requiredStack : requiredStacks) {
            if (!consumeRequiredItem(player, state, requiredStack)) {
                return false;
            }
        }
        return true;
    }

    private void mergeRequiredStack(List<ItemStack> stacks, ItemStack stack) {
        for (ItemStack existingStack : stacks) {
            if (ItemStack.isSameItemSameTags(existingStack, stack)) {
                existingStack.grow(stack.getCount());
                return;
            }
        }
        stacks.add(stack.copy());
    }

    private BlockState copyCompatibleProperties(BlockState source, BlockState target) {
        BlockState result = target;
        for (Property<?> sourceProperty : source.getProperties()) {
            Property<?> targetProperty = findProperty(result, sourceProperty.getName());
            if (targetProperty != null) {
                result = copyPropertyValue(source, result, sourceProperty, targetProperty);
            }
        }
        return result;
    }

    private @Nullable Property<?> findProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals(name)) {
                return property;
            }
        }
        return null;
    }

    private <T extends Comparable<T>> BlockState copyPropertyValue(BlockState source, BlockState target,
                                                                   Property<T> sourceProperty,
                                                                   Property<?> targetProperty) {
        T value = source.getValue(sourceProperty);
        return setIfAllowed(target, targetProperty, value);
    }

    private <T extends Comparable<T>> BlockState setIfAllowed(BlockState state, Property<?> property, T value) {
        return setIfAllowedTyped(state, property, value);
    }

    private <T extends Comparable<T>> BlockState setIfAllowedTyped(BlockState state, Property<?> property, T value) {
        @SuppressWarnings("unchecked")
        Property<T> typedProperty = (Property<T>) property;
        return typedProperty.getPossibleValues().contains(value) ? state.setValue(typedProperty, value) : state;
    }

    private ActionStartResult validateSelectionSize(MMSelection selection) {
        long maxScanVolume = MMConfig.MAX_SELECTION_SCAN_VOLUME.get();
        if (selection.scanVolume() > maxScanVolume) {
            return ActionStartResult.error(Component.translatable("message.matter_manipulator.selection_too_large",
                    selection.scanVolume(), maxScanVolume));
        }
        return null;
    }

    private boolean canRemoveMovedSource(Level level, Player player, Blueprint blueprint, BlueprintBlock block) {
        BlockPos sourcePos = blueprint.sourcePos(block);
        if (sourcePos == null || !level.dimension().location().equals(blueprint.sourceDimension())) {
            return false;
        }
        if (!level.isLoaded(sourcePos) || !level.getWorldBorder().isWithinBounds(sourcePos) ||
                !player.mayInteract(level, sourcePos)) {
            return false;
        }
        return BlockMovers.sourceStillMatches(level, sourcePos, block);
    }

    private boolean removeMovedSource(Level level, Player player, Blueprint blueprint, BlueprintBlock block) {
        if (!canRemoveMovedSource(level, player, blueprint, block)) {
            return false;
        }
        BlockPos sourcePos = blueprint.sourcePos(block);
        return sourcePos != null && level.destroyBlock(sourcePos, false, player);
    }

    private boolean selectionsIntersect(MMSelection first, MMSelection second) {
        if (!first.dimension().equals(second.dimension())) {
            return false;
        }
        return first.min().getX() <= second.max().getX() && first.max().getX() >= second.min().getX() &&
                first.min().getY() <= second.max().getY() && first.max().getY() >= second.min().getY() &&
                first.min().getZ() <= second.max().getZ() && first.max().getZ() >= second.min().getZ();
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

    private int effectiveBlocksPerOperation(MMState state) {
        int blocks = tier.placeSpeed() < 0 ? MK3_BLOCKS_PER_OPERATION : tier.placeSpeed();
        if (state.hasUpgrade(MMUpgrade.SPEED)) {
            blocks *= 2;
        }
        return Math.max(1, blocks);
    }

    private int effectivePlaceTicks(MMState state) {
        int ticks = Math.max(1, tier.placeTicks());
        if (state.hasUpgrade(MMUpgrade.SPEED)) {
            ticks = Math.max(1, ticks / 2);
        }
        return ticks;
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

    private record MissingItem(ItemStack stack, int available) {}

    private record PastePreflightResult(boolean valid, Component message) {

        private static final PastePreflightResult VALID = new PastePreflightResult(true, Component.empty());

        private static PastePreflightResult blocked(Component message) {
            return new PastePreflightResult(false, message);
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
