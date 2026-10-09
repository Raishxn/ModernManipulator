package com.raishxn.modern_manipulator.common.uplink;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHolder;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.google.common.collect.ImmutableSet;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendInfoToPlayer;

/**
 * The quantum uplink's ME connector hatch.
 * Provides the plans as fake processing patterns, and requests the auto plans so that the ME system crafts every
 * missing item. Once a plan's pattern is pushed, its inputs are returned to the ME system and the job is cancelled.
 */
public class MMUplinkMEHatchPartMachine extends MultiblockPartMachine
                                        implements IGridConnectedMachine, ICraftingProvider, ICraftingRequester,
                                        ICraftingSimulationRequester {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MMUplinkMEHatchPartMachine.class,
            MultiblockPartMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    protected final GridNodeHolder nodeHolder;

    @DescSynced
    @Getter
    @Setter
    protected boolean isOnline;

    protected final IActionSource actionSource;

    private final List<ManipulatorRequest> manualRequests = new ArrayList<>();
    private final List<ManipulatorRequest> autoRequests = new ArrayList<>();

    /** Items that were pushed by a plan's pattern, which need to be returned to the ME system. */
    private final List<GenericStack> pendingCraft = new ArrayList<>();

    private static int discriminator = 0;

    public MMUplinkMEHatchPartMachine(IMachineBlockEntity holder) {
        super(holder);
        this.nodeHolder = new GridNodeHolder(this);
        this.actionSource = IActionSource.ofMachine(nodeHolder.getMainNode()::getNode);

        getMainNode().addService(ICraftingProvider.class, this);
        getMainNode().addService(ICraftingRequester.class, this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public IManagedGridNode getMainNode() {
        return nodeHolder.getMainNode();
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (getLevel() instanceof ServerLevel) {
            subscribeServerTick(this::tick);
        }
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
        getMainNode().setExposedOnSides(EnumSet.of(newFacing));
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        IGridConnectedMachine.super.onMainNodeStateChanged(reason);
        ICraftingProvider.requestUpdate(getMainNode());
    }

    @Override
    public boolean canShared() {
        return false;
    }

    public @Nullable IGrid getGrid() {
        IGridNode node = getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    public @Nullable MEStorage getStorage() {
        IGrid grid = getGrid();
        return grid == null ? null : grid.getStorageService().getInventory();
    }

    public IActionSource getRequestSource() {
        return actionSource;
    }

    public boolean isPowered() {
        return getMainNode().isPowered();
    }

    public boolean isActive() {
        return getMainNode().isActive();
    }

    private void tick() {
        if (getOffsetTimer() % 20 != 0) return;

        updateMEStatus();

        pushPendingCraft();

        Iterator<ManipulatorRequest> iter = autoRequests.iterator();

        boolean changed = false;

        while (iter.hasNext()) {
            if (!iter.next().poll()) {
                iter.remove();
                changed = true;
            }
        }

        if (changed) onRequestsChanged();
    }

    // #region Crafting

    private void pushPendingCraft() {
        if (pendingCraft.isEmpty()) return;

        MEStorage storage = getStorage();

        if (storage == null) return;

        Iterator<GenericStack> iter = pendingCraft.iterator();

        while (iter.hasNext()) {
            GenericStack stack = iter.next();

            long inserted = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, actionSource);

            iter.remove();

            if (inserted < stack.amount()) {
                // should never happen, but don't delete items if it does
                pendingCraft.add(0, new GenericStack(stack.what(), stack.amount() - inserted));
                break;
            }
        }
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> patterns = new ArrayList<>();

        for (ManipulatorRequest request : manualRequests) {
            IPatternDetails pattern = request.getPattern();
            if (pattern != null) patterns.add(pattern);
        }

        for (ManipulatorRequest request : autoRequests) {
            IPatternDetails pattern = request.getPattern();
            if (pattern != null) patterns.add(pattern);
        }

        return patterns;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        pushPendingCraft();

        if (isBusy()) return false;

        for (KeyCounter counter : inputHolder) {
            for (var entry : counter) {
                pendingCraft.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }

        AEKey hologram = patternDetails.getPrimaryOutput().what();

        Iterator<ManipulatorRequest> iter = autoRequests.iterator();

        while (iter.hasNext()) {
            ManipulatorRequest request = iter.next();

            if (hologram.equals(request.getHologramKey())) {
                if (request.link != null) {
                    request.link.cancel();

                    Player player = request.getPlayer();

                    if (player != null) sendInfoToPlayer(player, "mm.info.craft_finished", request.requestName);
                }

                iter.remove();
            }
        }

        onRequestsChanged();

        pushPendingCraft();

        return true;
    }

    @Override
    public boolean isBusy() {
        return !pendingCraft.isEmpty();
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        ImmutableSet.Builder<ICraftingLink> links = ImmutableSet.builder();

        for (ManipulatorRequest request : autoRequests) {
            if (request.link != null) links.add(request.link);
        }

        return links.build();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        return 0;
    }

    @Override
    public void jobStateChange(ICraftingLink link) {}

    @Override
    public @Nullable IGridNode getActionableNode() {
        return getMainNode().getNode();
    }

    @Override
    public IActionSource getActionSource() {
        return actionSource;
    }

    @Override
    public @Nullable IGridNode getGridNode() {
        return getMainNode().getNode();
    }

    public void addRequest(Player requester, String requestName, List<BigItemStack> requiredItems, boolean autocraft) {
        ManipulatorRequest request = new ManipulatorRequest(this, requester.getUUID(), requestName, requiredItems,
                discriminator++);

        if (autocraft) {
            autoRequests.add(request);
        } else {
            manualRequests.add(request);
        }

        onRequestsChanged();
    }

    public void clearManualPlans(Player player) {
        manualRequests.removeIf(request -> request.requester.equals(player.getUUID()));

        onRequestsChanged();

        sendInfoToPlayer(player, "mm.info.cleared_manual_plans");
    }

    public void cancelAutoPlans(Player player) {
        Iterator<ManipulatorRequest> iter = autoRequests.iterator();

        while (iter.hasNext()) {
            ManipulatorRequest request = iter.next();

            if (request.requester.equals(player.getUUID())) {
                if (request.link != null) request.link.cancel();
                iter.remove();
            }
        }

        onRequestsChanged();

        sendInfoToPlayer(player, "mm.info.cancelled_auto_plans");
    }

    public boolean hasAnyRequests() {
        return !autoRequests.isEmpty() || !manualRequests.isEmpty();
    }

    private void onRequestsChanged() {
        ICraftingProvider.requestUpdate(getMainNode());
        markDirty();
    }

    // #endregion

    // #region Persistence

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);

        if (forDrop) return;

        ListTag manual = new ListTag();
        for (ManipulatorRequest request : manualRequests) manual.add(request.writeToNBT());
        tag.put("manualRequests", manual);

        ListTag auto = new ListTag();
        for (ManipulatorRequest request : autoRequests) auto.add(request.writeToNBT());
        tag.put("autoRequests", auto);

        ListTag pending = new ListTag();
        for (GenericStack stack : pendingCraft) pending.add(GenericStack.writeTag(stack));
        tag.put("pendingCraft", pending);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);

        manualRequests.clear();
        autoRequests.clear();
        pendingCraft.clear();

        for (Tag t : tag.getList("manualRequests", Tag.TAG_COMPOUND)) {
            ManipulatorRequest request = ManipulatorRequest.readFromNBT(this, (CompoundTag) t);
            if (request != null) manualRequests.add(request);
        }

        for (Tag t : tag.getList("autoRequests", Tag.TAG_COMPOUND)) {
            ManipulatorRequest request = ManipulatorRequest.readFromNBT(this, (CompoundTag) t);
            if (request != null) autoRequests.add(request);
        }

        for (Tag t : tag.getList("pendingCraft", Tag.TAG_COMPOUND)) {
            GenericStack stack = GenericStack.readTag((CompoundTag) t);
            if (stack != null) pendingCraft.add(stack);
        }
    }

    // #endregion

    private static class ManipulatorRequest {

        public final MMUplinkMEHatchPartMachine hatch;
        public final UUID requester;
        public final String requestName;
        public final List<BigItemStack> requiredItems;
        public final ItemStack hologram;

        public Future<ICraftingPlan> job;
        public ICraftingLink link;

        private IPatternDetails pattern;

        ManipulatorRequest(MMUplinkMEHatchPartMachine hatch, UUID requester, String requestName,
                           List<BigItemStack> requiredItems,
                           int discriminator) {
            this.hatch = hatch;
            this.requester = requester;
            this.requestName = requestName;
            this.requiredItems = requiredItems;

            hologram = new ItemStack(MMItems.HOLOGRAM.get());
            hologram.setHoverName(Component.literal(requestName).withStyle(ChatFormatting.RESET));

            // add a number so that holograms with the same name are still different
            hologram.getOrCreateTag().putInt("discriminator", discriminator);
        }

        public AEItemKey getHologramKey() {
            return AEItemKey.of(hologram);
        }

        public @Nullable Player getPlayer() {
            var server = ServerLifecycleHooks.getCurrentServer();
            return server == null ? null : server.getPlayerList().getPlayer(requester);
        }

        public CompoundTag writeToNBT() {
            CompoundTag tag = new CompoundTag();

            tag.putUUID("requester", requester);
            tag.putString("name", requestName);
            tag.putInt("discriminator", hologram.getOrCreateTag().getInt("discriminator"));

            ListTag items = new ListTag();

            for (BigItemStack item : requiredItems) {
                CompoundTag itemTag = item.getId().writeToNBT();
                itemTag.putLong("amount", item.getStackSize());
                items.add(itemTag);
            }

            tag.put("items", items);

            return tag;
        }

        public static @Nullable ManipulatorRequest readFromNBT(MMUplinkMEHatchPartMachine hatch, CompoundTag tag) {
            try {
                List<BigItemStack> items = new ArrayList<>();

                for (Tag t : tag.getList("items", Tag.TAG_COMPOUND)) {
                    CompoundTag itemTag = (CompoundTag) t;
                    items.add(BigItemStack.create(com.raishxn.modern_manipulator.common.utils.ItemId.create(itemTag),
                            itemTag.getLong("amount")));
                }

                if (items.isEmpty()) return null;

                return new ManipulatorRequest(hatch, tag.getUUID("requester"), tag.getString("name"), items,
                        tag.getInt("discriminator"));
            } catch (Exception e) {
                ModernManipulator.LOG.error("Could not load manipulator plan", e);
                return null;
            }
        }

        /**
         * Creates a fake processing pattern representing this request.
         */
        public @Nullable IPatternDetails getPattern() {
            if (pattern == null) {
                if (hatch == null || hatch.getLevel() == null) return null;

                List<GenericStack> inputs = new ArrayList<>();

                for (BigItemStack item : requiredItems) {
                    AEItemKey key = AEItemKey.of(item.getId().getItemStack());
                    if (key != null) inputs.add(new GenericStack(key, item.getStackSize()));
                }

                ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                        inputs.toArray(new GenericStack[0]),
                        new GenericStack[] { new GenericStack(getHologramKey(), 1) });

                pattern = PatternDetailsHelper.decodePattern(encoded, hatch.getLevel());
            }

            return pattern;
        }

        /**
         * Check the job future and crafting link.
         * If the job future has finished, submit the plan.
         * If the crafting link was cancelled, tell the hatch to remove this request.
         * The crafting job will never actually get completed.
         * It gets cancelled and this request is removed when the fake pattern is pushed.
         *
         * @return False when this request should be removed
         */
        boolean poll() {
            if (!hatch.isActive()) return true;

            IGrid grid = hatch.getGrid();

            if (grid == null) return true;

            if (link != null) return !link.isCanceled();

            var crafting = grid.getCraftingService();

            if (job == null) {
                job = crafting.beginCraftingCalculation(hatch.getLevel(), hatch, getHologramKey(), 1,
                        CalculationStrategy.REPORT_MISSING_ITEMS);
            }

            if (job == null) return false;

            if (!job.isDone()) return true;

            try {
                ICraftingPlan plan = job.get();

                job = null;

                if (plan == null) return false;

                Player player = getPlayer();

                if (plan.simulation()) {
                    if (player != null) sendInfoToPlayer(player, "mm.info.plan_missing_items", requestName);
                    return false;
                }

                var result = crafting.submitJob(plan, hatch, null, false, hatch.getRequestSource());

                if (!result.successful() || result.link() == null) {
                    if (player != null) com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer(player,
                            "mm.info.error.craft_failed", requestName);
                    return false;
                }

                link = result.link();

                if (player != null) sendInfoToPlayer(player, "mm.info.submitted_job", requestName);
            } catch (Exception e) {
                ModernManipulator.LOG.error("Could not submit the crafting job for plan {}", requestName, e);
                Player player = getPlayer();
                if (player != null) com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer(player,
                        "mm.info.error.craft_failed", requestName);
                return false;
            }

            return true;
        }
    }
}
