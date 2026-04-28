package com.raishxn.modern_manipulator.common.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import com.raishxn.modern_manipulator.common.item.MMCapability;
import com.raishxn.modern_manipulator.common.item.MMState;
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
            case CLEAR_COORDS -> {
                state.clearCoords();
                MatterManipulatorItem.setState(stack, state);
                player.displayClientMessage(Component.translatable("message.matter_manipulator.coords_cleared"),
                        true);
            }
        }
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
        CLEAR_COORDS
    }
}
