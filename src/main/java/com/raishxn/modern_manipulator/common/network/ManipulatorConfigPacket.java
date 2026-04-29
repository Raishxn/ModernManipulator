package com.raishxn.modern_manipulator.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import com.raishxn.modern_manipulator.common.integration.ae2.AE2Integration;
import com.raishxn.modern_manipulator.common.item.MMCapability;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.BlockSelectMode;
import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import com.raishxn.modern_manipulator.common.item.MMState.RemoveMode;
import com.raishxn.modern_manipulator.common.item.MMState.Shape;
import com.raishxn.modern_manipulator.common.item.MMState.ToolMode;
import com.raishxn.modern_manipulator.common.item.MatterManipulatorItem;

import java.util.function.Supplier;

public record ManipulatorConfigPacket(Action action) {

    public static void encode(ManipulatorConfigPacket packet, FriendlyByteBuf buffer) {
        buffer.writeEnum(packet.action);
    }

    public static ManipulatorConfigPacket decode(FriendlyByteBuf buffer) {
        return new ManipulatorConfigPacket(buffer.readEnum(Action.class));
    }

    public static void handle(ManipulatorConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                apply(player, packet.action);
            }
        });
        context.setPacketHandled(true);
    }

    private static void apply(ServerPlayer player, Action action) {
        ItemStack stack = selectedManipulator(player);
        if (!(stack.getItem() instanceof MatterManipulatorItem manipulator)) {
            return;
        }

        MMState state = MatterManipulatorItem.getState(stack);
        switch (action) {
            case NEXT_MODE -> setMode(player, stack, manipulator, state, 1);
            case PREVIOUS_MODE -> setMode(player, stack, manipulator, state, -1);
            case NEXT_SHAPE -> setShape(player, stack, state, 1);
            case PREVIOUS_SHAPE -> setShape(player, stack, state, -1);
            case SET_MODE_GEOMETRY -> setMode(player, stack, manipulator, state, ToolMode.GEOMETRY);
            case SET_MODE_COPYING -> setMode(player, stack, manipulator, state, ToolMode.COPYING);
            case SET_MODE_EXCHANGING -> setMode(player, stack, manipulator, state, ToolMode.EXCHANGING);
            case SET_MODE_MOVING -> setMode(player, stack, manipulator, state, ToolMode.MOVING);
            case SET_MODE_CABLES -> setMode(player, stack, manipulator, state, ToolMode.CABLES);
            case SET_SHAPE_LINE -> setShape(player, stack, state, Shape.LINE);
            case SET_SHAPE_CUBE -> setShape(player, stack, state, Shape.CUBE);
            case SET_SHAPE_SPHERE -> setShape(player, stack, state, Shape.SPHERE);
            case SET_SHAPE_CYLINDER -> setShape(player, stack, state, Shape.CYLINDER);
            case PREPARE_COPY -> prepareCopy(player, stack, manipulator, state);
            case PREPARE_MOVE -> prepareMove(player, stack, manipulator, state);
            case PREPARE_PASTE -> preparePaste(player, stack, state);
            case EXECUTE_COPY -> executeModeAction(player, stack, manipulator, state, ToolMode.COPYING);
            case EXECUTE_CUT -> executeModeAction(player, stack, manipulator, state, ToolMode.MOVING);
            case EXECUTE_PASTE -> executePaste(player, stack, manipulator, state);
            case SET_COORD_A -> setLookedCoord(player, stack, manipulator, state, CoordSlot.A);
            case SET_COORD_B -> setLookedCoord(player, stack, manipulator, state, CoordSlot.B);
            case SET_COORD_C -> setLookedCoord(player, stack, manipulator, state, CoordSlot.C);
            case MOVE_COORD_A -> setLookedCoord(player, stack, manipulator, state, CoordSlot.A);
            case MOVE_COORD_B -> setLookedCoord(player, stack, manipulator, state, CoordSlot.B);
            case MOVE_COORD_C -> setLookedCoord(player, stack, manipulator, state, CoordSlot.C);
            case MOVE_ALL_COORDS -> moveSelectionHere(player, stack, manipulator, state);
            case MOVE_SELECTION_HERE -> moveSelectionHere(player, stack, manipulator, state);
            case ROTATE_CW -> updateTransform(player, stack, state, TransformAction.ROTATE_CW);
            case ROTATE_CCW -> updateTransform(player, stack, state, TransformAction.ROTATE_CCW);
            case MIRROR_X -> updateTransform(player, stack, state, TransformAction.MIRROR_X);
            case MIRROR_Y -> updateTransform(player, stack, state, TransformAction.MIRROR_Y);
            case MIRROR_Z -> updateTransform(player, stack, state, TransformAction.MIRROR_Z);
            case RESET_TRANSFORM -> updateTransform(player, stack, state, TransformAction.RESET);
            case ARRAY_X_PLUS -> updatePasteArray(player, stack, state, 1, 0, 0);
            case ARRAY_X_MINUS -> updatePasteArray(player, stack, state, -1, 0, 0);
            case ARRAY_Y_PLUS -> updatePasteArray(player, stack, state, 0, 1, 0);
            case ARRAY_Y_MINUS -> updatePasteArray(player, stack, state, 0, -1, 0);
            case ARRAY_Z_PLUS -> updatePasteArray(player, stack, state, 0, 0, 1);
            case ARRAY_Z_MINUS -> updatePasteArray(player, stack, state, 0, 0, -1);
            case ARRAY_RESET -> resetPasteArray(player, stack, state);
            case OFFSET_X_PLUS -> updatePasteOffset(player, stack, state, 1, 0, 0);
            case OFFSET_X_MINUS -> updatePasteOffset(player, stack, state, -1, 0, 0);
            case OFFSET_Y_PLUS -> updatePasteOffset(player, stack, state, 0, 1, 0);
            case OFFSET_Y_MINUS -> updatePasteOffset(player, stack, state, 0, -1, 0);
            case OFFSET_Z_PLUS -> updatePasteOffset(player, stack, state, 0, 0, 1);
            case OFFSET_Z_MINUS -> updatePasteOffset(player, stack, state, 0, 0, -1);
            case OFFSET_RESET -> resetPasteOffset(player, stack, state);
            case SET_REMOVE_NONE -> setRemoveMode(player, stack, state, RemoveMode.NONE);
            case SET_REMOVE_REPLACEABLE -> setRemoveMode(player, stack, state, RemoveMode.REPLACEABLE);
            case SET_REMOVE_ALL -> setRemoveMode(player, stack, state, RemoveMode.ALL);
            case SET_BLOCK_SELECT_NONE -> setBlockSelectMode(player, stack, state, BlockSelectMode.NONE);
            case SET_BLOCK_SELECT_CORNERS -> setBlockSelectMode(player, stack, state, BlockSelectMode.CORNERS);
            case SET_BLOCK_SELECT_EDGES -> setBlockSelectMode(player, stack, state, BlockSelectMode.EDGES);
            case SET_BLOCK_SELECT_FACES -> setBlockSelectMode(player, stack, state, BlockSelectMode.FACES);
            case SET_BLOCK_SELECT_VOLUMES -> setBlockSelectMode(player, stack, state, BlockSelectMode.VOLUMES);
            case SET_BLOCK_SELECT_ALL -> setBlockSelectMode(player, stack, state, BlockSelectMode.ALL);
            case SET_EXCHANGE_REPLACEMENT -> setExchangeReplacement(player, stack, manipulator, state);
            case ADD_EXCHANGE_WHITELIST_BLOCK -> addExchangeWhitelistBlock(player, stack, manipulator, state);
            case CLEAR_EXCHANGE_WHITELIST -> clearExchangeWhitelist(player, stack, state);
            case SET_CABLE -> setCable(player, stack, manipulator, state);
            case CLEAR_CABLE -> clearCable(player, stack, state);
            case UNIMPLEMENTED_RADIAL_OPTION -> player.displayClientMessage(
                    Component.translatable("message.matter_manipulator.radial.not_implemented"), true);
            case RUN_ACTION -> manipulator.runConfiguredAction(stack, player, player.level());
            case TOGGLE_PENDING_PAUSE -> togglePendingPause(player, stack, state);
            case CANCEL_PENDING -> cancelPending(player, stack, state);
            case CLEAR_BLUEPRINT -> {
                state.clearBlueprint();
                MatterManipulatorItem.setState(stack, state);
                player.displayClientMessage(Component.translatable("message.matter_manipulator.blueprint_cleared"),
                        true);
            }
            case CHECK_ME_DOWNLINK -> player.displayClientMessage(AE2Integration.status(player, state), true);
            case CLEAR_ME_DOWNLINK -> {
                state.clearMeDownlink();
                MatterManipulatorItem.setState(stack, state);
                player.displayClientMessage(Component.translatable("message.matter_manipulator.downlink.cleared"),
                        true);
            }
            case RESET -> {
                state.clearCoords();
                state.clearBlueprint();
                state.clearPendingAction();
                state.clearMeDownlink();
                state.resetTransform();
                state.resetPasteArray();
                MatterManipulatorItem.setState(stack, state);
                player.displayClientMessage(Component.translatable("message.matter_manipulator.reset"), true);
            }
            case CLEAR_COORDS -> {
                state.clearCoords();
                MatterManipulatorItem.setState(stack, state);
                player.displayClientMessage(Component.translatable("message.matter_manipulator.coords_cleared"),
                        true);
            }
        }
    }

    private static void togglePendingPause(ServerPlayer player, ItemStack stack, MMState state) {
        MMState.PendingAction pendingAction = state.pendingAction();
        if (pendingAction == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.pending.none"), true);
            return;
        }
        pendingAction.togglePaused();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable(pendingAction.paused() ?
                "message.matter_manipulator.pending.paused" :
                "message.matter_manipulator.pending.resumed"), true);
    }

    private static void cancelPending(ServerPlayer player, ItemStack stack, MMState state) {
        if (state.pendingAction() == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.pending.none"), true);
            return;
        }
        state.clearPendingAction();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.pending.cancelled"), true);
    }

    private static void setRemoveMode(ServerPlayer player, ItemStack stack, MMState state, RemoveMode removeMode) {
        state.setRemoveMode(removeMode);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.remove_mode_set",
                removeMode.displayName()), true);
    }

    private static void setBlockSelectMode(ServerPlayer player, ItemStack stack, MMState state,
                                           BlockSelectMode blockSelectMode) {
        state.setBlockSelectMode(blockSelectMode);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.block_select_mode_set",
                blockSelectMode.displayName()), true);
    }

    private static void setExchangeReplacement(ServerPlayer player, ItemStack stack,
                                               MatterManipulatorItem manipulator, MMState state) {
        BlockPos targetPos = lookedBlock(player, manipulator.tier());
        ItemStack replacementStack = ItemStack.EMPTY;
        BlockState replacementState = null;
        if (targetPos != null) {
            replacementState = player.level().getBlockState(targetPos);
            if (!replacementState.isAir()) {
                replacementStack = new ItemStack(replacementState.getBlock());
            }
        }
        if (replacementStack.isEmpty() && player.getOffhandItem().getItem() instanceof BlockItem blockItem) {
            replacementStack = player.getOffhandItem().copyWithCount(1);
            replacementState = blockItem.getBlock().defaultBlockState();
        }
        if (replacementStack.isEmpty() || replacementState == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.exchange.no_block"), true);
            return;
        }

        state.setExchangeReplacement(replacementStack, replacementState);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.exchange.replacement_set",
                replacementStack.getHoverName()), true);
    }

    private static void addExchangeWhitelistBlock(ServerPlayer player, ItemStack stack,
                                                  MatterManipulatorItem manipulator, MMState state) {
        BlockPos targetPos = lookedBlock(player, manipulator.tier());
        if (targetPos == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.coord.no_target"), true);
            return;
        }
        BlockState blockState = player.level().getBlockState(targetPos);
        if (blockState.isAir()) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.exchange.whitelist.no_air"),
                    true);
            return;
        }

        state.addExchangeWhitelistBlock(blockState);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.exchange.whitelist_added",
                blockState.getBlock().getName(), state.exchangeWhitelist().size()), true);
    }

    private static void clearExchangeWhitelist(ServerPlayer player, ItemStack stack, MMState state) {
        state.clearExchangeWhitelist();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.exchange.whitelist_cleared"),
                true);
    }

    private static void setCable(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                 MMState state) {
        BlockPos targetPos = lookedBlock(player, manipulator.tier());
        ItemStack cableStack = ItemStack.EMPTY;
        BlockState cableState = null;
        if (targetPos != null) {
            cableState = player.level().getBlockState(targetPos);
            if (!cableState.isAir()) {
                cableStack = new ItemStack(cableState.getBlock());
            }
        }
        if (cableStack.isEmpty() && player.getOffhandItem().getItem() instanceof BlockItem blockItem) {
            cableStack = player.getOffhandItem().copyWithCount(1);
            cableState = blockItem.getBlock().defaultBlockState();
        }
        if (cableStack.isEmpty() || cableState == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.cables.no_cable"), true);
            return;
        }
        if (!com.raishxn.modern_manipulator.common.building.BlockMovers.isCableLike(cableState)) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.cables.not_cable"), true);
            return;
        }

        state.setCable(cableStack, cableState);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.cables.cable_set",
                cableStack.getHoverName()), true);
    }

    private static void clearCable(ServerPlayer player, ItemStack stack, MMState state) {
        state.clearCable();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.cables.cable_cleared"), true);
    }

    private static void updateTransform(ServerPlayer player, ItemStack stack, MMState state, TransformAction action) {
        switch (action) {
            case ROTATE_CW -> state.rotatePaste(1);
            case ROTATE_CCW -> state.rotatePaste(-1);
            case MIRROR_X -> state.toggleMirrorX();
            case MIRROR_Y -> state.toggleMirrorY();
            case MIRROR_Z -> state.toggleMirrorZ();
            case RESET -> state.resetTransform();
        }
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.transform_set",
                state.rotationY() * 90,
                state.mirrorX() ? Component.translatable("message.matter_manipulator.on") :
                        Component.translatable("message.matter_manipulator.off"),
                state.mirrorY() ? Component.translatable("message.matter_manipulator.on") :
                        Component.translatable("message.matter_manipulator.off"),
                state.mirrorZ() ? Component.translatable("message.matter_manipulator.on") :
                        Component.translatable("message.matter_manipulator.off")),
                true);
    }

    private static void updatePasteArray(ServerPlayer player, ItemStack stack, MMState state,
                                         int deltaX, int deltaY, int deltaZ) {
        state.adjustPasteArray(deltaX, deltaY, deltaZ);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.array_set",
                state.pasteArrayX(), state.pasteArrayY(), state.pasteArrayZ()), true);
    }

    private static void resetPasteArray(ServerPlayer player, ItemStack stack, MMState state) {
        state.resetPasteArray();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.array_set",
                state.pasteArrayX(), state.pasteArrayY(), state.pasteArrayZ()), true);
    }

    private static void updatePasteOffset(ServerPlayer player, ItemStack stack, MMState state,
                                          int deltaX, int deltaY, int deltaZ) {
        state.adjustPasteOffset(deltaX, deltaY, deltaZ);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.offset_set",
                state.pasteOffsetX(), state.pasteOffsetY(), state.pasteOffsetZ()), true);
    }

    private static void resetPasteOffset(ServerPlayer player, ItemStack stack, MMState state) {
        state.resetPasteOffset();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.offset_set",
                state.pasteOffsetX(), state.pasteOffsetY(), state.pasteOffsetZ()), true);
    }

    private static void setLookedCoord(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                       MMState state, CoordSlot coordSlot) {
        BlockPos targetPos = lookedBlock(player, manipulator.tier());
        if (targetPos == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.coord.no_target"), true);
            return;
        }

        MarkedPosition markedPosition = new MarkedPosition(player.level().dimension().location(), targetPos);
        switch (coordSlot) {
            case A -> state.setCoordA(markedPosition);
            case B -> state.setCoordB(markedPosition);
            case C -> state.setCoordC(markedPosition);
        }
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable(coordSlot.messageKey(), markedPosition.shortText(),
                selectionInfo(state)), true);
    }

    private static Component selectionInfo(MMState state) {
        return state.selection() == null ? Component.translatable("message.matter_manipulator.selection_incomplete") :
                Component.literal(state.selection().describe());
    }

    private static BlockPos lookedBlock(ServerPlayer player, MatterManipulatorItem.ManipulatorTier tier) {
        double range = tier.maxRange() < 0 ? 256.0D : Math.max(5.0D, tier.maxRange());
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        HitResult hitResult = player.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        if (hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult blockHitResult)) {
            return null;
        }
        return blockHitResult.getBlockPos();
    }

    private static void moveSelectionHere(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                          MMState state) {
        BlockPos targetPos = lookedBlock(player, manipulator.tier());
        MarkedPosition coordA = state.coordA();
        if (targetPos == null) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.coord.no_target"), true);
            return;
        }
        if (coordA == null) {
            state.setCoordA(new MarkedPosition(player.level().dimension().location(), targetPos));
            MatterManipulatorItem.setState(stack, state);
            player.displayClientMessage(Component.translatable("message.matter_manipulator.coord_a_set",
                    state.coordA().shortText(), selectionInfo(state)), true);
            return;
        }
        if (!coordA.dimension().equals(player.level().dimension().location())) {
            player.displayClientMessage(Component.translatable("message.matter_manipulator.remove.wrong_dimension"),
                    true);
            return;
        }

        BlockPos delta = targetPos.subtract(coordA.pos());
        state.setCoordA(new MarkedPosition(player.level().dimension().location(), targetPos));
        if (state.coordB() != null && state.coordB().dimension().equals(player.level().dimension().location())) {
            state.setCoordB(
                    new MarkedPosition(player.level().dimension().location(), state.coordB().pos().offset(delta)));
        }
        if (state.coordC() != null && state.coordC().dimension().equals(player.level().dimension().location())) {
            state.setCoordC(
                    new MarkedPosition(player.level().dimension().location(), state.coordC().pos().offset(delta)));
        }
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.coords_moved",
                targetPos.getX(), targetPos.getY(), targetPos.getZ(), selectionInfo(state)), true);
    }

    private static void prepareCopy(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                    MMState state) {
        if (!canUseMode(state, manipulator.tier(), ToolMode.COPYING)) {
            return;
        }
        state.setMode(ToolMode.COPYING);
        state.clearCoordC();
        state.clearBlueprint();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.copy.prepare"), true);
    }

    private static void prepareMove(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                    MMState state) {
        if (!canUseMode(state, manipulator.tier(), ToolMode.MOVING)) {
            return;
        }
        state.setMode(ToolMode.MOVING);
        state.clearCoordC();
        state.clearBlueprint();
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.move.prepare"), true);
    }

    private static void preparePaste(ServerPlayer player, ItemStack stack, MMState state) {
        if (state.mode() != ToolMode.MOVING) {
            state.setMode(ToolMode.COPYING);
        }
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.paste.prepare"), true);
    }

    private static void executeModeAction(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                          MMState state, ToolMode mode) {
        executeModeAction(player, stack, manipulator, state, mode, true);
    }

    private static void executeModeAction(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                          MMState state, ToolMode mode, boolean clearPasteTarget) {
        if (!canUseMode(state, manipulator.tier(), mode)) {
            return;
        }
        state.setMode(mode);
        if (clearPasteTarget && (mode == ToolMode.COPYING || mode == ToolMode.MOVING)) {
            state.clearCoordC();
        }
        MatterManipulatorItem.setState(stack, state);
        manipulator.runConfiguredAction(stack, player, player.level());
    }

    private static void executePaste(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator,
                                     MMState state) {
        MMState.Blueprint blueprint = state.blueprint();
        if (blueprint != null && blueprint.movesSource()) {
            executeModeAction(player, stack, manipulator, state, ToolMode.MOVING, false);
            return;
        }
        executeModeAction(player, stack, manipulator, state, ToolMode.COPYING, false);
    }

    private static void setMode(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator, MMState state,
                                ToolMode mode) {
        if (!canUseMode(state, manipulator.tier(), mode)) {
            return;
        }
        state.setMode(mode);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.mode_set",
                mode.displayName()), true);
    }

    private static void setMode(ServerPlayer player, ItemStack stack, MatterManipulatorItem manipulator, MMState state,
                                int direction) {
        ToolMode[] modes = ToolMode.values();
        int start = state.mode().ordinal();
        for (int i = 1; i <= modes.length; i++) {
            ToolMode candidate = modes[Math.floorMod(start + i * direction, modes.length)];
            if (canUseMode(state, manipulator.tier(), candidate)) {
                state.setMode(candidate);
                MatterManipulatorItem.setState(stack, state);
                player.displayClientMessage(Component.translatable("message.matter_manipulator.mode_set",
                        candidate.displayName()), true);
                return;
            }
        }
    }

    private static void setShape(ServerPlayer player, ItemStack stack, MMState state, Shape shape) {
        state.setShape(shape);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.shape_set",
                shape.displayName()), true);
    }

    private static void setShape(ServerPlayer player, ItemStack stack, MMState state, int direction) {
        Shape[] shapes = Shape.values();
        Shape candidate = shapes[Math.floorMod(state.shape().ordinal() + direction, shapes.length)];
        state.setShape(candidate);
        MatterManipulatorItem.setState(stack, state);
        player.displayClientMessage(Component.translatable("message.matter_manipulator.shape_set",
                candidate.displayName()), true);
    }

    private static boolean canUseMode(MMState state, MatterManipulatorItem.ManipulatorTier tier, ToolMode mode) {
        return switch (mode) {
            case GEOMETRY -> state.hasCapability(tier, MMCapability.ALLOW_GEOMETRY);
            case COPYING -> state.hasCapability(tier, MMCapability.ALLOW_COPYING);
            case EXCHANGING -> state.hasCapability(tier, MMCapability.ALLOW_EXCHANGING);
            case MOVING -> state.hasCapability(tier, MMCapability.ALLOW_MOVING);
            case CABLES -> state.hasCapability(tier, MMCapability.ALLOW_CABLES);
        };
    }

    private static ItemStack selectedManipulator(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof MatterManipulatorItem) {
            return mainHand;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof MatterManipulatorItem) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    private enum CoordSlot {

        A("message.matter_manipulator.coord_a_set"),
        B("message.matter_manipulator.coord_b_set"),
        C("message.matter_manipulator.coord_c_set");

        private final String messageKey;

        CoordSlot(String messageKey) {
            this.messageKey = messageKey;
        }

        private String messageKey() {
            return messageKey;
        }
    }

    private enum TransformAction {
        ROTATE_CW,
        ROTATE_CCW,
        MIRROR_X,
        MIRROR_Y,
        MIRROR_Z,
        RESET
    }

    public enum Action {
        NEXT_MODE,
        PREVIOUS_MODE,
        NEXT_SHAPE,
        PREVIOUS_SHAPE,
        SET_MODE_GEOMETRY,
        SET_MODE_COPYING,
        SET_MODE_EXCHANGING,
        SET_MODE_MOVING,
        SET_MODE_CABLES,
        SET_SHAPE_LINE,
        SET_SHAPE_CUBE,
        SET_SHAPE_SPHERE,
        SET_SHAPE_CYLINDER,
        PREPARE_COPY,
        PREPARE_MOVE,
        PREPARE_PASTE,
        EXECUTE_COPY,
        EXECUTE_CUT,
        EXECUTE_PASTE,
        SET_COORD_A,
        SET_COORD_B,
        SET_COORD_C,
        MOVE_COORD_A,
        MOVE_COORD_B,
        MOVE_COORD_C,
        MOVE_ALL_COORDS,
        MOVE_SELECTION_HERE,
        ROTATE_CW,
        ROTATE_CCW,
        MIRROR_X,
        MIRROR_Y,
        MIRROR_Z,
        RESET_TRANSFORM,
        ARRAY_X_PLUS,
        ARRAY_X_MINUS,
        ARRAY_Y_PLUS,
        ARRAY_Y_MINUS,
        ARRAY_Z_PLUS,
        ARRAY_Z_MINUS,
        ARRAY_RESET,
        OFFSET_X_PLUS,
        OFFSET_X_MINUS,
        OFFSET_Y_PLUS,
        OFFSET_Y_MINUS,
        OFFSET_Z_PLUS,
        OFFSET_Z_MINUS,
        OFFSET_RESET,
        SET_REMOVE_NONE,
        SET_REMOVE_REPLACEABLE,
        SET_REMOVE_ALL,
        SET_BLOCK_SELECT_NONE,
        SET_BLOCK_SELECT_CORNERS,
        SET_BLOCK_SELECT_EDGES,
        SET_BLOCK_SELECT_FACES,
        SET_BLOCK_SELECT_VOLUMES,
        SET_BLOCK_SELECT_ALL,
        SET_EXCHANGE_REPLACEMENT,
        ADD_EXCHANGE_WHITELIST_BLOCK,
        CLEAR_EXCHANGE_WHITELIST,
        SET_CABLE,
        CLEAR_CABLE,
        RUN_ACTION,
        TOGGLE_PENDING_PAUSE,
        CANCEL_PENDING,
        CLEAR_BLUEPRINT,
        CHECK_ME_DOWNLINK,
        CLEAR_ME_DOWNLINK,
        UNIMPLEMENTED_RADIAL_OPTION,
        RESET,
        CLEAR_COORDS
    }
}
