package com.raishxn.modern_manipulator.common.compat.ae;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import com.raishxn.modern_manipulator.common.compat.MEConnection;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import com.raishxn.modern_manipulator.common.utils.ItemId;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A connection to an ME network through the wireless access point the manipulator was linked to.
 */
public class AEConnection implements MEConnection {

    private final IWirelessAccessPoint accessPoint;
    private final IGrid grid;

    private IWirelessAccessPoint prevAccessPoint;

    private AEConnection(IWirelessAccessPoint accessPoint, IGrid grid) {
        this.accessPoint = accessPoint;
        this.grid = grid;
    }

    public static @Nullable MEConnection connect(Location link) {
        Level world = link.getWorld();

        if (world == null || !world.isLoaded(link.toPos())) return null;

        BlockEntity te = world.getBlockEntity(link.toPos());

        if (!(te instanceof IWirelessAccessPoint wap)) return null;

        IGrid grid = wap.getGrid();

        if (grid == null) return null;

        return new AEConnection(wap, grid);
    }

    @Override
    public boolean isConnected() {
        return grid != null && grid.getStorageService() != null;
    }

    private MEStorage storage() {
        return grid.getStorageService().getInventory();
    }

    private IActionSource source(Player player) {
        return IActionSource.ofPlayer(player, accessPoint);
    }

    @Override
    public boolean canInteract(Player player) {
        if (grid == null) return false;

        if (!grid.getEnergyService().isNetworkPowered()) return false;

        if (checkDistance(player, prevAccessPoint)) return true;

        for (WirelessAccessPointBlockEntity wap : grid.getActiveMachines(WirelessAccessPointBlockEntity.class)) {
            if (checkDistance(player, wap)) {
                prevAccessPoint = wap;
                return true;
            }
        }

        prevAccessPoint = null;

        return false;
    }

    private boolean checkDistance(Player player, IWirelessAccessPoint wap) {
        if (wap == null || wap.getGrid() != grid || !wap.isActive()) return false;

        var location = wap.getLocation();

        if (location.getLevel() != player.level()) return false;

        var pos = location.getPos();

        double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

        return Math.pow(wap.getRange(), 2) >= distance;
    }

    @Override
    public Object getStorageIdentity() {
        return grid;
    }

    @Override
    public List<BigItemStack> extractItems(BigItemStack request, boolean fuzzy, boolean simulate, Player player) {
        List<BigItemStack> out = new ArrayList<>();

        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;

        if (!fuzzy) {
            AEItemKey key = AEItemKey.of(request.getItemStack());

            if (key == null) return out;

            long extracted = storage().extract(key, request.getStackSize(), mode, source(player));

            if (extracted > 0) out.add(request.copy().setStackSize(extracted));

            return out;
        }

        long remaining = request.getStackSize();

        for (var entry : storage().getAvailableStacks()) {
            if (remaining <= 0) break;

            AEKey key = entry.getKey();

            if (!(key instanceof AEItemKey itemKey) || itemKey.getItem() != request.getItem()) continue;

            long extracted = storage().extract(itemKey, remaining, mode, source(player));

            if (extracted > 0) {
                remaining -= extracted;
                out.add(BigItemStack.create(ItemId.create(itemKey.getItem(), itemKey.getTag()), extracted));
            }
        }

        return out;
    }

    @Override
    public long injectItems(BigItemStack stack, Player player) {
        AEItemKey key = AEItemKey.of(stack.getItemStack());

        if (key == null) return stack.getStackSize();

        long inserted = storage().insert(key, stack.getStackSize(), Actionable.MODULATE, source(player));

        return stack.getStackSize() - inserted;
    }

    @Override
    public long injectFluids(BigFluidStack stack, Player player) {
        AEFluidKey key = AEFluidKey.of(stack.getFluidStack());

        if (key == null) return stack.getStackSize();

        long inserted = storage().insert(key, stack.getStackSize(), Actionable.MODULATE, source(player));

        return stack.getStackSize() - inserted;
    }
}
