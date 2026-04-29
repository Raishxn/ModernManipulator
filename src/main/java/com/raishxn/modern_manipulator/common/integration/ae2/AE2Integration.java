package com.raishxn.modern_manipulator.common.integration.ae2;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;

public final class AE2Integration {

    private AE2Integration() {}

    public static boolean isLinked(ServerPlayer player, MMState state) {
        return storage(player, state) != null;
    }

    public static Component status(ServerPlayer player, MMState state) {
        MarkedPosition downlink = state.meDownlink();
        if (downlink == null) {
            return Component.translatable("tooltip.matter_manipulator.me_downlink_unset");
        }
        IGridNode node = exposedNode(player, downlink);
        if (node == null) {
            return Component.translatable("tooltip.matter_manipulator.me_downlink_missing", downlink.shortText());
        }
        if (!node.isOnline()) {
            return Component.translatable("tooltip.matter_manipulator.me_downlink_offline", downlink.shortText());
        }
        return Component.translatable("tooltip.matter_manipulator.me_downlink_online",
                storage(player, state).getDescription(), downlink.shortText());
    }

    public static int countItem(ServerPlayer player, MMState state, ItemStack wanted) {
        MEStorage storage = storage(player, state);
        if (storage == null || wanted.isEmpty()) {
            return 0;
        }
        long available = storage.extract(AEItemKey.of(wanted), Integer.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.ofPlayer(player));
        return available > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) available;
    }

    public static boolean extractItem(ServerPlayer player, MMState state, ItemStack wanted, int amount) {
        MEStorage storage = storage(player, state);
        if (storage == null || wanted.isEmpty() || amount <= 0) {
            return amount <= 0;
        }
        long extracted = storage.extract(AEItemKey.of(wanted), amount, Actionable.MODULATE,
                IActionSource.ofPlayer(player));
        return extracted == amount;
    }

    public static ItemStack insertItem(ServerPlayer player, MMState state, ItemStack stack) {
        MEStorage storage = storage(player, state);
        if (storage == null || stack.isEmpty()) {
            return stack;
        }
        ItemStack remainder = stack.copy();
        long inserted = storage.insert(AEItemKey.of(remainder), remainder.getCount(), Actionable.MODULATE,
                IActionSource.ofPlayer(player));
        if (inserted <= 0) {
            return remainder;
        }
        remainder.shrink(inserted > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) inserted);
        return remainder;
    }

    public static boolean hasGridNode(ServerPlayer player, MarkedPosition downlink) {
        return exposedNode(player, downlink) != null;
    }

    private static MEStorage storage(ServerPlayer player, MMState state) {
        MarkedPosition downlink = state.meDownlink();
        if (downlink == null) {
            return null;
        }
        IGridNode node = exposedNode(player, downlink);
        if (node == null || !node.isOnline()) {
            return null;
        }
        return node.getGrid().getService(IStorageService.class).getInventory();
    }

    private static IGridNode exposedNode(ServerPlayer player, MarkedPosition downlink) {
        if (!downlink.dimension().equals(player.level().dimension().location()) ||
                !player.level().isLoaded(downlink.pos())) {
            return null;
        }
        for (Direction direction : Direction.values()) {
            IGridNode node = GridHelper.getExposedNode(player.level(), downlink.pos(), direction);
            if (node != null) {
                return node;
            }
        }
        return GridHelper.getExposedNode(player.level(), downlink.pos(), null);
    }
}
