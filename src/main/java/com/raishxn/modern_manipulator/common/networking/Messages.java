package com.raishxn.modern_manipulator.common.networking;

import com.raishxn.modern_manipulator.CommonProxy;
import com.raishxn.modern_manipulator.GlobalMMConfig.DebugConfig;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.BlockRemoveMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.BlockSelectMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PendingAction;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.Shape;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Every packet the manipulator sends.
 * Entries can be reordered (both sides always have the same enum).
 */
public enum Messages {

    MMBPressed(server(simple((player, stack, manipulator, state) -> manipulator.onMMBPressed(player, stack, state)))),
    MMBPressedInGUI(server(cursorItemStackPacket((player, stack, manipulator, state, isSneak, hoveredStack) -> {
        ItemMatterManipulator.onMMBPressedInGUI(player, stack, state, isSneak, hoveredStack);
    }))),
    SetRemoveMode(server(enumPacket(BlockRemoveMode.values(), (state, value) -> state.config.removeMode = value))),
    SetPlaceMode(server(enumPacket(PlaceMode.values(), (player, stack, manipulator, state, value) -> {
        int requiredBit = switch (value) {
            case COPYING -> ItemMatterManipulator.ALLOW_COPYING;
            case EXCHANGING -> ItemMatterManipulator.ALLOW_EXCHANGING;
            case GEOMETRY -> 0;
            case MOVING -> ItemMatterManipulator.ALLOW_MOVING;
            case CABLES -> ItemMatterManipulator.ALLOW_CABLES;
        };

        if (state.hasCap(requiredBit)) {
            state.config.placeMode = value;
        }
    }))),
    SetBlockSelectMode(server(enumPacket(BlockSelectMode.values(), (state, value) -> state.config.blockSelectMode = value))),
    SetPendingAction(server(enumPacket(PendingAction.values(), (state, value) -> state.config.action = value))),
    ClearBlocks(server(simple((player, stack, manipulator, state) -> {
        state.config.corners = null;
        state.config.edges = null;
        state.config.faces = null;
        state.config.volumes = null;
        state.config.action = null;
    }))),
    SetShape(server(enumPacket(Shape.values(), (state, value) -> state.config.shape = value))),
    SetLinkExternalHubs(server(simple((player, stack, manipulator, state) -> {
        state.config.linkExternalHubs = !state.config.linkExternalHubs;
    }))),
    SetA(server(locationPacket((player, stack, manipulator, state, location) -> {
        state.config.coordA = new Location(player.level(), location);
    }))),
    MoveA(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MOVING_COORDS;
        state.config.coordAOffset = new Vector3i();
        state.config.coordBOffset = null;
        state.config.coordCOffset = null;
    }))),
    SetB(server(locationPacket((player, stack, manipulator, state, location) -> {
        state.config.coordB = new Location(player.level(), location);
    }))),
    MoveB(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MOVING_COORDS;
        state.config.coordAOffset = null;
        state.config.coordBOffset = new Vector3i();
        state.config.coordCOffset = null;
    }))),
    SetC(server(locationPacket((player, stack, manipulator, state, location) -> {
        state.config.coordC = new Location(player.level(), location);
    }))),
    MoveC(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MOVING_COORDS;
        state.config.coordAOffset = null;
        state.config.coordBOffset = null;
        state.config.coordCOffset = new Vector3i();
    }))),
    SwapRegion(server(simple((player, stack, manipulator, state) -> {
        Location coordA = state.config.coordA;
        Location coordB = state.config.coordB;
        Location coordC = state.config.coordC;

        if (coordA == null || coordB == null || coordC == null) return;

        Vector3i newCoordBVector = new Vector3i(
            coordC.x + coordB.x - coordA.x,
            coordC.y + coordB.y - coordA.y,
            coordC.z + coordB.z - coordA.z);

        state.config.coordA = coordC;
        state.config.coordB = new Location(player.level(), newCoordBVector);
        state.config.coordC = coordA;
    }))),
    MoveAll(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MOVING_COORDS;

        Vector3i lookingAt = MMUtils.getLookingAtLocation(player);

        state.config.coordAOffset = state.config.coordA == null ? null : state.config.coordA.toVec().sub(lookingAt);
        state.config.coordBOffset = state.config.coordB == null ? null : state.config.coordB.toVec().sub(lookingAt);
        state.config.coordCOffset = state.config.coordC == null ? null : state.config.coordC.toVec().sub(lookingAt);
    }))),
    MoveHere(server(simple((player, stack, manipulator, state) -> {
        if (state.config.shape.requiresC()) {
            if (Location.areCompatible(state.config.coordA, state.config.coordB, state.config.coordC)) {
                Vector3i offsetB = state.config.coordB.toVec().sub(state.config.coordA.toVec());
                Vector3i offsetC = state.config.coordC.toVec().sub(state.config.coordA.toVec());

                Vector3i newA = MMUtils.getLookingAtLocation(player);
                Vector3i newB = new Vector3i(newA).add(offsetB);
                Vector3i newC = new Vector3i(newA).add(offsetC);

                state.config.coordA = new Location(player.level(), newA);
                state.config.coordB = new Location(player.level(), newB);
                state.config.coordC = new Location(player.level(), newC);
            }
        } else {
            if (Location.areCompatible(state.config.coordA, state.config.coordB)) {
                Vector3i offsetB = state.config.coordB.toVec().sub(state.config.coordA.toVec());

                Vector3i newA = MMUtils.getLookingAtLocation(player);
                Vector3i newB = new Vector3i(newA).add(offsetB);

                state.config.coordA = new Location(player.level(), newA);
                state.config.coordB = new Location(player.level(), newB);
            }
        }
    }))),
    ClearCoords(server(simple((player, stack, manipulator, state) -> {
        state.config.action = null;
        state.config.coordA = null;
        state.config.coordB = null;
        state.config.coordC = null;
        state.config.coordAOffset = null;
        state.config.coordBOffset = null;
        state.config.coordCOffset = null;
    }))),
    ClearTransform(server(simple((player, stack, manipulator, state) -> {
        state.config.transform = new Transform();
        state.config.arraySpan = null;
    }))),
    MarkCopy(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MARK_COPY_A;
        state.config.coordA = null;
        state.config.coordB = null;
    }))),
    MarkCut(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MARK_CUT_A;
        state.config.coordA = null;
        state.config.coordB = null;
    }))),
    MarkPaste(server(simple((player, stack, manipulator, state) -> {
        state.config.action = PendingAction.MARK_PASTE;
        state.config.coordC = null;
    }))),
    GetRequiredItems(server(intPacket((player, stack, manipulator, state, value) -> {
        if (state.config.placeMode != PlaceMode.COPYING) return;

        PlanHelper.createPlanImpl(player, state, manipulator, value);
    }))),
    ClearManualPlans(server(simple((player, stack, manipulator, state) -> {
        if (state.connectToUplink()) {
            state.uplink.clearManualPlans(player);
        }
    }))),
    CancelAutoPlans(server(simple((player, stack, manipulator, state) -> {
        if (state.connectToUplink()) {
            state.uplink.cancelAutoPlans(player);
        }
    }))),
    ClearWhitelist(server(simple((player, stack, manipulator, state) -> state.config.replaceWhitelist = null))),
    TooltipResponse(client(new IPacketHandler<Integer>() {

        @Override
        public void encode(Integer value, FriendlyByteBuf buf) {
            buf.writeVarInt(value);
        }

        @Override
        public Integer decode(FriendlyByteBuf buf) {
            return buf.readVarInt();
        }

        @Override
        public void handle(Player player, Integer value) {
            ItemMatterManipulator.onTooltipResponse(value);
        }
    })),
    TooltipQuery(server(new IPacketHandler<Integer>() {

        @Override
        public void encode(Integer value, FriendlyByteBuf buf) {
            buf.writeVarInt(value);
        }

        @Override
        public Integer decode(FriendlyByteBuf buf) {
            return buf.readVarInt();
        }

        @Override
        public void handle(Player player, Integer slot) {
            if (slot < 0 || slot >= player.containerMenu.slots.size()) return;

            ItemStack stack = player.containerMenu.getSlot(slot).getItem();

            if (stack.getItem() instanceof ItemMatterManipulator) {
                MMState state = ItemMatterManipulator.getState(stack);

                int result = 0;

                if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_AE)) {
                    if (state.connectToMESystem()) {
                        if (state.canInteractWithAE(player)) {
                            result |= MMUtils.TOOLTIP_AE_WORKS;
                        }
                    }
                }

                if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_UPLINK)) {
                    if (state.uplinkAddress != null) {
                        if (state.connectToUplink()) {
                            result |= MMUtils.TOOLTIP_UPLINK_WORKS;
                        }
                    }
                }

                Messages.TooltipResponse.sendToPlayer((ServerPlayer) player, result);
            }
        }
    })),
    SetReplaceCribs(server(simple((player, stack, manipulator, state) -> {
        state.config.replaceCribsWithProxies = !state.config.replaceCribsWithProxies;
    }))),
    SetReplaceInterfaces(server(simple((player, stack, manipulator, state) -> {
        state.config.replaceInterfacesWithP2P = !state.config.replaceInterfacesWithP2P;
    }))),
    SetArray(server(locationPacket((player, stack, manipulator, state, span) -> state.config.arraySpan = span))),
    ResetArray(server(simple((player, stack, manipulator, state) -> state.config.arraySpan = null))),
    ResetTransform(server(simple((player, stack, manipulator, state) -> state.config.transform = new Transform()))),
    ToggleTransformFlip(server(intPacket((player, stack, manipulator, state, value) -> {
        Transform transform = state.config.transform;
        if (transform == null) state.config.transform = (transform = new Transform());

        if ((value & Transform.FLIP_X) != 0) transform.flipX ^= true;
        if ((value & Transform.FLIP_Y) != 0) transform.flipY ^= true;
        if ((value & Transform.FLIP_Z) != 0) transform.flipZ ^= true;
    }))),
    RotateTransform(server(intPacket((player, stack, manipulator, state, value) -> {
        if (state.config.transform == null) state.config.transform = new Transform();

        int dir = value & 0xFF;

        if (dir < 0 || dir >= Direction.values().length) return;

        int amount = ((value >> 8) & 0xFF) != 0 ? 1 : -1;

        state.config.transform.rotate(Direction.from3DDataValue(dir), amount);
    }))),
    BuildStatus(client(new IPacketHandler<Pair<LongList, LongList>>() {

        @Override
        public void encode(Pair<LongList, LongList> value, FriendlyByteBuf buf) {
            buf.writeLongArray(value.left().toLongArray());
            buf.writeLongArray(value.right().toLongArray());
        }

        @Override
        public Pair<LongList, LongList> decode(FriendlyByteBuf buf) {
            return Pair.of(new LongArrayList(buf.readLongArray()), new LongArrayList(buf.readLongArray()));
        }

        @Override
        public void handle(Player player, Pair<LongList, LongList> value) {
            CommonProxy.setStatusHints(value.left(), value.right());
        }
    })),
    UpdateUplinkState(client(new IPacketHandler<Pair<Location, Integer>>() {

        @Override
        public void encode(Pair<Location, Integer> value, FriendlyByteBuf buf) {
            buf.writeUtf(value.left().worldId);
            buf.writeBlockPos(value.left().toPos());
            buf.writeVarInt(value.right());
        }

        @Override
        public Pair<Location, Integer> decode(FriendlyByteBuf buf) {
            String world = buf.readUtf();
            var pos = buf.readBlockPos();
            return Pair.of(new Location(world, pos.getX(), pos.getY(), pos.getZ()), buf.readVarInt());
        }

        @Override
        public void handle(Player player, Pair<Location, Integer> value) {
            CommonProxy.setUplinkState(value.left(), value.right());
        }
    })),

    ;

    // #region Channel

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        ModernManipulator.id("main"),
        () -> PROTOCOL,
        PROTOCOL::equals,
        PROTOCOL::equals);

    private static boolean registered = false;

    public static void init() {
        if (registered) return;
        registered = true;

        CHANNEL.registerMessage(
            0,
            MMPacket.class,
            MMPacket::encode,
            MMPacket::decode,
            MMPacket::handle,
            Optional.empty());
    }

    // #endregion

    private final Side side;
    private final IPacketHandler<Object> handler;

    @SuppressWarnings("unchecked")
    Messages(SidedHandler<?> handler) {
        this.side = handler.side;
        this.handler = (IPacketHandler<Object>) handler.handler;
    }

    public void sendToServer() {
        sendToServer(null);
    }

    public void sendToServer(@Nullable Object data) {
        if (DebugConfig.debug()) {
            ModernManipulator.LOG.info("Sending packet to server: " + this + "; " + data);
        }

        CHANNEL.sendToServer(new MMPacket(this, data));
    }

    public void sendToPlayer(ServerPlayer player) {
        sendToPlayer(player, null);
    }

    public void sendToPlayer(ServerPlayer player, @Nullable Object data) {
        if (DebugConfig.debug()) {
            ModernManipulator.LOG.info("Sending packet to player: " + this + "; " + data + "; " + player);
        }

        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MMPacket(this, data));
    }

    public void sendToPlayersAround(Location location, @Nullable Object data) {
        var world = location.getWorld();

        if (world == null) return;

        CHANNEL.send(
            PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(location.x, location.y, location.z, 256, world.dimension())),
            new MMPacket(this, data));
    }

    // #region Packet

    public static class MMPacket {

        private final Messages message;
        private final Object data;

        public MMPacket(Messages message, Object data) {
            this.message = message;
            this.data = data;
        }

        public static void encode(MMPacket packet, FriendlyByteBuf buf) {
            buf.writeVarInt(packet.message.ordinal());
            packet.message.handler.encode(packet.data, buf);
        }

        public static MMPacket decode(FriendlyByteBuf buf) {
            int id = buf.readVarInt();

            Messages[] values = Messages.values();

            if (id < 0 || id >= values.length) throw new IllegalArgumentException("Invalid manipulator packet id " + id);

            Messages message = values[id];

            return new MMPacket(message, message.handler.decode(buf));
        }

        public static void handle(MMPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
            NetworkEvent.Context ctx = ctxSupplier.get();

            NetworkDirection direction = ctx.getDirection();

            if (packet.message.side == Side.SERVER && direction == NetworkDirection.PLAY_TO_SERVER) {
                ServerPlayer player = ctx.getSender();

                ctx.enqueueWork(() -> {
                    if (player != null) packet.message.handler.handle(player, packet.data);
                });
            } else if (packet.message.side == Side.CLIENT && direction == NetworkDirection.PLAY_TO_CLIENT) {
                ctx.enqueueWork(() -> packet.message.handler.handle(CommonProxy.getClientPlayer(), packet.data));
            }

            ctx.setPacketHandled(true);
        }
    }

    private enum Side {
        CLIENT,
        SERVER
    }

    private record SidedHandler<T> (Side side, IPacketHandler<T> handler) {}

    private static <T> SidedHandler<T> server(IPacketHandler<T> handler) {
        return new SidedHandler<>(Side.SERVER, handler);
    }

    private static <T> SidedHandler<T> client(IPacketHandler<T> handler) {
        return new SidedHandler<>(Side.CLIENT, handler);
    }

    public interface IPacketHandler<T> {

        void encode(T value, FriendlyByteBuf buf);

        T decode(FriendlyByteBuf buf);

        void handle(Player player, T value);
    }

    // #endregion

    // #region Manipulator packets

    private interface IStateHandler {

        void handle(Player player, ItemStack stack, ItemMatterManipulator manipulator, MMState state);
    }

    private interface IStateHandlerWith<T> {

        void handle(Player player, ItemStack stack, ItemMatterManipulator manipulator, MMState state, T value);
    }

    private interface ISimpleStateHandlerWith<T> {

        void handle(MMState state, T value);
    }

    private interface ICursorStackHandler {

        void handle(Player player, ItemStack stack, ItemMatterManipulator manipulator, MMState state, boolean isSneak, ItemStack hovered);
    }

    /**
     * Runs the handler on the player's held manipulator and saves its state afterwards.
     */
    private static <T> void withManipulator(Player player, T value, IStateHandlerWith<T> handler) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (held.getItem() instanceof ItemMatterManipulator manipulator) {
            MMState state = ItemMatterManipulator.getState(held);

            handler.handle(player, held, manipulator, state, value);

            ItemMatterManipulator.setState(held, state);
        }
    }

    private static IPacketHandler<Object> simple(IStateHandler handler) {
        return new IPacketHandler<>() {

            @Override
            public void encode(Object value, FriendlyByteBuf buf) {}

            @Override
            public Object decode(FriendlyByteBuf buf) {
                return null;
            }

            @Override
            public void handle(Player player, Object value) {
                withManipulator(player, value, (p, stack, manipulator, state, v) -> handler.handle(p, stack, manipulator, state));
            }
        };
    }

    private static IPacketHandler<Integer> intPacket(IStateHandlerWith<Integer> handler) {
        return new IPacketHandler<>() {

            @Override
            public void encode(Integer value, FriendlyByteBuf buf) {
                buf.writeVarInt(value == null ? 0 : value);
            }

            @Override
            public Integer decode(FriendlyByteBuf buf) {
                return buf.readVarInt();
            }

            @Override
            public void handle(Player player, Integer value) {
                withManipulator(player, value, handler);
            }
        };
    }

    private static <E extends Enum<E>> IPacketHandler<E> enumPacket(E[] values, ISimpleStateHandlerWith<E> handler) {
        return enumPacket(values, (player, stack, manipulator, state, value) -> handler.handle(state, value));
    }

    private static <E extends Enum<E>> IPacketHandler<E> enumPacket(E[] values, IStateHandlerWith<E> handler) {
        return new IPacketHandler<>() {

            @Override
            public void encode(E value, FriendlyByteBuf buf) {
                buf.writeVarInt(value == null ? -1 : value.ordinal());
            }

            @Override
            public E decode(FriendlyByteBuf buf) {
                int i = buf.readVarInt();
                return i < 0 || i >= values.length ? null : values[i];
            }

            @Override
            public void handle(Player player, E value) {
                withManipulator(player, value, handler);
            }
        };
    }

    private static IPacketHandler<Vector3i> locationPacket(IStateHandlerWith<Vector3i> handler) {
        return new IPacketHandler<>() {

            @Override
            public void encode(Vector3i value, FriendlyByteBuf buf) {
                buf.writeBoolean(value != null);

                if (value != null) {
                    buf.writeInt(value.x);
                    buf.writeInt(value.y);
                    buf.writeInt(value.z);
                }
            }

            @Override
            public Vector3i decode(FriendlyByteBuf buf) {
                if (!buf.readBoolean()) return null;

                return new Vector3i(buf.readInt(), buf.readInt(), buf.readInt());
            }

            @Override
            public void handle(Player player, Vector3i value) {
                withManipulator(player, value, handler);
            }
        };
    }

    public record CursorStack(boolean isSneak, ItemStack hovered) {}

    private static IPacketHandler<CursorStack> cursorItemStackPacket(ICursorStackHandler handler) {
        return new IPacketHandler<>() {

            @Override
            public void encode(CursorStack value, FriendlyByteBuf buf) {
                buf.writeBoolean(value.isSneak());
                buf.writeItem(value.hovered());
            }

            @Override
            public CursorStack decode(FriendlyByteBuf buf) {
                return new CursorStack(buf.readBoolean(), buf.readItem());
            }

            @Override
            public void handle(Player player, CursorStack value) {
                withManipulator(
                    player,
                    value,
                    (p, stack, manipulator, state, v) -> handler.handle(p, stack, manipulator, state, v.isSneak(), v.hovered()));
            }
        };
    }

    // #endregion
}
