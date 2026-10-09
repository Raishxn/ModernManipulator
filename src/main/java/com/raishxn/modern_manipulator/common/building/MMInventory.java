package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.uplink.IUplinkMulti;
import com.raishxn.modern_manipulator.common.uplink.UplinkStatus;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import com.raishxn.modern_manipulator.common.utils.FluidId;
import com.raishxn.modern_manipulator.common.utils.ItemId;
import com.raishxn.modern_manipulator.common.utils.MMUtils;
import it.unimi.dsi.fastutil.booleans.BooleanObjectImmutablePair;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendWarningToPlayer;

/**
 * Handles all manipulator-related item sourcing and sinking.
 */
public class MMInventory implements IPseudoInventory {

    public Player player;
    public MMState state;
    public ManipulatorTier tier;

    public final Object2LongOpenHashMap<ItemId> pendingItems = new Object2LongOpenHashMap<>();
    public final Object2LongOpenHashMap<FluidId> pendingFluids = new Object2LongOpenHashMap<>();

    private boolean printedUplinkWarning = false;

    private final HashSet<Object> visitedGrids = new HashSet<>();

    public MMInventory(Player player, MMState state, ManipulatorTier tier) {
        this.player = player;
        this.state = state;
        this.tier = tier;
    }

    @Override
    public BooleanObjectImmutablePair<List<BigItemStack>> tryConsumeItems(List<BigItemStack> items, int flags) {
        if ((flags & CONSUME_IGNORE_CREATIVE) == 0 && player.isCreative()) {
            return BooleanObjectImmutablePair.of(true, items);
        } else {
            visitedGrids.clear();

            List<BigItemStack> simulated = MMUtils.mapToList(items, BigItemStack::copy);
            List<BigItemStack> extracted = new ArrayList<>();

            // the first pass is simulated to make sure the requested items can be provided
            consumeItemsFromPending(simulated, extracted, flags | CONSUME_SIMULATED);
            consumeItemsFromPlayer(simulated, extracted, flags | CONSUME_SIMULATED);
            if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_AE)) {
                consumeItemsFromAE(simulated, extracted, flags | CONSUME_SIMULATED);
            }
            if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_UPLINK)) {
                consumeItemsFromUplink(simulated, extracted, flags | CONSUME_SIMULATED);
            }

            // if we aren't allowed to partially consume items, make sure everything was consumed
            if ((flags & CONSUME_PARTIAL) == 0) {
                if (simulated.stream().anyMatch(s -> s.getStackSize() > 0))
                    return BooleanObjectImmutablePair.of(false, null);
            }

            if ((flags & CONSUME_SIMULATED) != 0) return BooleanObjectImmutablePair.of(true, merge(extracted));

            visitedGrids.clear();

            simulated = MMUtils.mapToList(items, BigItemStack::copy);
            extracted.clear();

            consumeItemsFromPending(simulated, extracted, flags);
            consumeItemsFromPlayer(simulated, extracted, flags);
            if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_AE)) {
                consumeItemsFromAE(simulated, extracted, flags);
            }
            if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_UPLINK)) {
                consumeItemsFromUplink(simulated, extracted, flags);
            }

            return BooleanObjectImmutablePair.of(true, merge(extracted));
        }
    }

    private static ArrayList<BigItemStack> merge(List<BigItemStack> unmerged) {
        Map<ItemId, BigItemStack> out = new LinkedHashMap<>();

        for (BigItemStack ex : unmerged) {
            BigItemStack merged = out.computeIfAbsent(ex.getId(), id -> BigItemStack.create(id, 0));

            merged.incStackSize(ex.getStackSize());
        }

        return new ArrayList<>(out.values());
    }

    @Override
    public void givePlayerItems(List<BigItemStack> items) {
        if (player.isCreative()) return;

        for (BigItemStack item : items) {
            if (item != null && item.getStackSize() > 0) {
                pendingItems.addTo(item.getId(), item.stackSize);
            }
        }
    }

    @Override
    public void givePlayerFluids(List<BigFluidStack> fluids) {
        if (player.isCreative()) return;

        for (BigFluidStack fluid : fluids) {
            if (fluid != null && fluid.amount > 0) {
                pendingFluids.addTo(fluid.getId(), fluid.amount);
            }
        }
    }

    /**
     * Actually delivers stuff stored in pendingItems/pendingFluids so that the inserts are batched.
     * First tries to insert into AE, then the uplink, then the player's inventory.
     * If items can't be inserted, they're dropped on the ground.
     * If fluids can't be inserted, they're voided.
     */
    protected void actuallyGivePlayerStuff() {
        if (player.isCreative()) {
            pendingItems.clear();
            pendingFluids.clear();
            return;
        }

        boolean hasME = false;

        if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_AE)) {
            if (state.meLink != null && !state.hasMEConnection()) {
                state.connectToMESystem();
            }
            hasME = state.hasMEConnection() && state.canInteractWithAE(player);
        }

        boolean hasUplink = false;

        if (state.hasCap(ItemMatterManipulator.CONNECTS_TO_UPLINK)) {
            if (state.uplinkAddress != null && !state.hasUplinkConnection()) {
                state.connectToUplink();
            }
            hasUplink = state.hasUplinkConnection();
        }

        for (var entry : pendingItems.object2LongEntrySet()) {
            BigItemStack stack = BigItemStack.create(entry.getKey(), entry.getLongValue());

            if (hasME) {
                stack.setStackSize(state.me.injectItems(stack, player));

                if (stack.getStackSize() == 0) continue;
            }

            if (hasUplink) {
                injectItemsIntoUplink(stack);

                if (stack.getStackSize() == 0) continue;
            }

            injectItemsIntoInventory(stack);

            if (stack.getStackSize() == 0) continue;

            injectItemsIntoWorld(stack);
        }

        pendingItems.clear();

        for (var entry : pendingFluids.object2LongEntrySet()) {
            BigFluidStack stack = BigFluidStack.create(entry.getKey(), entry.getLongValue());

            if (hasME) {
                stack.setStackSize(state.me.injectFluids(stack, player));

                if (stack.getStackSize() == 0) continue;
            }

            if (hasUplink) {
                injectFluidsIntoUplink(stack);

                if (stack.getStackSize() == 0) continue;
            }

            injectFluidsIntoIdealCell(stack);

            if (stack.getStackSize() == 0) continue;

            injectFluidsIntoCells(stack);

            if (stack.getStackSize() == 0) continue;

            if (stack.amount > 0 && !player.isCreative()) {
                sendWarningToPlayer(player, "mm.info.warning.not_find_container_for_fluid");
                sendWarningToPlayer(
                        player,
                        "mm.info.warning.of_fluid",
                        MMUtils.formatNumber(stack.amount) + " L",
                        stack.getFluidStack().getDisplayName());
            }
        }

        pendingFluids.clear();
    }

    private void injectItemsIntoUplink(BigItemStack stack) {
        UplinkStatus status = state.uplink.tryGivePlayerItems(Collections.singletonList(stack));

        if (status != UplinkStatus.OK && !printedUplinkWarning) {
            printedUplinkWarning = true;
            sendErrorToPlayer(
                    player,
                    "mm.info.error.could_not_push_items_to_uplink",
                    Component.translatable(status.toUnlocalizedString()));
        }
    }

    private void injectItemsIntoInventory(BigItemStack stack) {
        while (stack.stackSize > 0) {
            ItemStack smallStack = stack.remove(stack.getItemStack().getMaxStackSize());

            int toInsert = smallStack.getCount();

            player.getInventory().add(smallStack);

            stack.stackSize += smallStack.getCount();

            if (smallStack.getCount() == toInsert) break;
        }
    }

    private void injectItemsIntoWorld(BigItemStack stack) {
        for (ItemStack smallStack : stack.toStacks()) {
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), smallStack);
            entity.setNoPickUpDelay();
            player.level().addFreshEntity(entity);
        }

        stack.setStackSize(0);
    }

    private void injectFluidsIntoUplink(BigFluidStack stack) {
        UplinkStatus status = state.uplink.tryGivePlayerFluids(Collections.singletonList(stack));

        if (status != UplinkStatus.OK && !printedUplinkWarning) {
            printedUplinkWarning = true;
            sendErrorToPlayer(
                    player,
                    "mm.info.error.could_not_push_fluids_to_uplink",
                    Component.translatable(status.toUnlocalizedString()));
        }
    }

    private static int getCapacity(IFluidHandlerItem handler) {
        int capacity = 0;
        for (int i = 0; i < handler.getTanks(); i++) {
            capacity += handler.getTankCapacity(i);
        }
        return capacity;
    }

    /**
     * Finds the smallest single container that can hold the whole fluid stack.
     */
    private void injectFluidsIntoIdealCell(BigFluidStack stack) {
        final FluidStack fluid = stack.getFluidStack();

        NonNullList<ItemStack> inv = player.getInventory().items;

        int bestSlot = -1;
        int bestCapacity = Integer.MAX_VALUE;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack slot = inv.get(i);

            if (slot.isEmpty() || slot.getCount() != 1) continue;

            IFluidHandlerItem handler = slot.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);

            if (handler == null) continue;

            if (handler.fill(fluid, IFluidHandler.FluidAction.SIMULATE) != fluid.getAmount()) continue;

            int capacity = getCapacity(handler);

            if (capacity < bestCapacity) {
                bestCapacity = capacity;
                bestSlot = i;
            }
        }

        if (bestSlot != -1) {
            ItemStack slot = inv.get(bestSlot);
            IFluidHandlerItem handler = slot.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);

            stack.amount -= handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);

            inv.set(bestSlot, handler.getContainer());
        }
    }

    private void injectFluidsIntoCells(BigFluidStack stack) {
        NonNullList<ItemStack> inv = player.getInventory().items;

        for (int i = 0; i < inv.size() && stack.amount > 0; i++) {
            ItemStack cell = inv.get(i);
            if (cell.isEmpty()) continue;

            if (!cell.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent()) continue;

            // Split cells off the stack one at a time and fill them
            while (stack.amount > 0 && !cell.isEmpty()) {
                ItemStack single = cell.copyWithCount(1);

                IFluidHandlerItem handler = single.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
                if (handler == null) break;

                FluidStack fluid = stack.getFluidStack();
                if (handler.fill(fluid, IFluidHandler.FluidAction.SIMULATE) <= 0) break;

                int filled = handler.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
                if (filled <= 0) break;

                stack.amount -= filled;
                cell.shrink(1);

                ItemStack filledContainer = handler.getContainer();

                if (!player.getInventory().add(filledContainer)) {
                    player.drop(filledContainer, false);
                }
            }
        }

        player.getInventory().setChanged();
    }

    private void consumeItemsFromPending(List<BigItemStack> requestedItems, List<BigItemStack> extractedItems,
                                         int flags) {
        boolean simulate = (flags & CONSUME_SIMULATED) != 0;
        boolean fuzzy = (flags & CONSUME_FUZZY) != 0;

        for (BigItemStack req : requestedItems) {
            if (req.getStackSize() == 0) continue;

            if (!fuzzy) {
                ItemId id = req.getId();

                long amtInPending = pendingItems.getLong(id);

                if (amtInPending == 0) continue;

                long toRemove = Math.min(amtInPending, req.getStackSize());

                extractedItems.add(req.copy().setStackSize(toRemove));
                amtInPending -= toRemove;
                req.decStackSize(toRemove);

                if (!simulate) {
                    if (amtInPending == 0) {
                        pendingItems.removeLong(id);
                    } else {
                        pendingItems.put(id, amtInPending);
                    }
                }
            } else {
                var iter = pendingItems.object2LongEntrySet().iterator();

                while (iter.hasNext()) {
                    var e = iter.next();

                    if (e.getLongValue() == 0) continue;

                    if (e.getKey().item() != req.getItem()) continue;

                    long amtInPending = e.getLongValue();
                    long toRemove = Math.min(amtInPending, req.getStackSize());

                    extractedItems.add(BigItemStack.create(e.getKey(), toRemove));
                    amtInPending -= toRemove;
                    req.decStackSize(toRemove);

                    if (!simulate) {
                        if (amtInPending == 0) {
                            iter.remove();
                        } else {
                            e.setValue(amtInPending);
                        }
                    }
                }
            }
        }
    }

    private void consumeItemsFromPlayer(List<BigItemStack> requestedItems, List<BigItemStack> extractedItems,
                                        int flags) {
        boolean simulate = (flags & CONSUME_SIMULATED) != 0;
        boolean fuzzy = (flags & CONSUME_FUZZY) != 0;

        NonNullList<ItemStack> inv = player.getInventory().items;

        for (BigItemStack req : requestedItems) {
            if (req.getStackSize() == 0) continue;

            for (int i = 0; i < inv.size(); i++) {
                ItemStack slot = inv.get(i);

                if (req.getStackSize() == 0) break;

                if (slot.isEmpty()) continue;

                if (req.getItem() != slot.getItem()) continue;

                if (!fuzzy && !req.isSameType(slot)) continue;

                int toRemove = (int) Math.min(slot.getCount(), req.getStackSize());

                req.decStackSize(toRemove);
                extractedItems.add(BigItemStack.create(slot).setStackSize(toRemove));

                if (!simulate) {
                    slot.shrink(toRemove);
                    if (slot.isEmpty()) {
                        inv.set(i, ItemStack.EMPTY);
                    }
                    player.getInventory().setChanged();
                }
            }
        }
    }

    private void consumeItemsFromAE(List<BigItemStack> requestedItems, List<BigItemStack> extractedItems, int flags) {
        boolean simulate = (flags & CONSUME_SIMULATED) != 0;
        boolean fuzzy = (flags & CONSUME_FUZZY) != 0;

        if (state.meLink == null) return;

        if (!state.hasMEConnection()) {
            if (!state.connectToMESystem()) return;
        }

        if (!state.canInteractWithAE(player)) return;

        if (!visitedGrids.add(state.me.getStorageIdentity())) return;

        for (BigItemStack req : requestedItems) {
            if (req.getStackSize() == 0) continue;

            for (BigItemStack result : state.me.extractItems(req.copy(), fuzzy, simulate, player)) {
                extractedItems.add(result);
                req.decStackSize(result.getStackSize());
            }
        }
    }

    private void consumeItemsFromUplink(List<BigItemStack> requestedItems, List<BigItemStack> extractedItems,
                                        int flags) {
        boolean simulate = (flags & CONSUME_SIMULATED) != 0;
        boolean fuzzy = (flags & CONSUME_FUZZY) != 0;

        if (state.uplinkAddress == null) return;

        state.connectToUplink();

        if (!state.hasUplinkConnection()) return;

        IUplinkMulti uplink = state.uplink;

        if (!visitedGrids.add(uplink.getStorageIdentity())) return;

        var result = uplink.tryConsumeItems(requestedItems, simulate, fuzzy);

        if (result.left() != UplinkStatus.OK && !printedUplinkWarning) {
            printedUplinkWarning = true;
            sendErrorToPlayer(
                    player,
                    "mm.info.error.could_not_request_items_from_uplink",
                    Component.translatable(result.left().toUnlocalizedString()));
        }

        if (result.right() != null) extractedItems.addAll(result.right());
    }

    protected void resetWarnings() {
        printedUplinkWarning = false;
    }
}
