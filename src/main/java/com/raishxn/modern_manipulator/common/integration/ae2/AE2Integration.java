package com.raishxn.modern_manipulator.common.integration.ae2;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.item.MMState;
import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AE2Integration {

    private static final Map<UUID, PendingCraftBatch> PENDING_CRAFTS = new ConcurrentHashMap<>();

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

    public static Component requestAutoCrafting(ServerPlayer player, MMState state, List<ItemStack> requests) {
        MarkedPosition downlink = state.meDownlink();
        if (downlink == null) {
            return Component.translatable("message.matter_manipulator.plan.auto_no_me");
        }
        IGridNode node = exposedNode(player, downlink);
        if (node == null || !node.isOnline()) {
            return Component.translatable("message.matter_manipulator.plan.auto_me_offline");
        }
        ICraftingService craftingService = node.getGrid().getService(ICraftingService.class);
        if (craftingService == null) {
            return Component.translatable("message.matter_manipulator.plan.auto_no_crafting_service");
        }

        List<PendingCraft> pending = new ArrayList<>();
        int skipped = 0;
        long requestedTotal = 0L;
        IActionSource actionSource = actionSource(player, node);
        for (ItemStack request : requests) {
            if (request.isEmpty() || request.getCount() <= 0) {
                continue;
            }
            AEItemKey key = AEItemKey.of(request);
            if (key == null || !craftingService.isCraftable(key)) {
                skipped++;
                continue;
            }
            long amount = request.getCount();
            requestedTotal += amount;
            Future<ICraftingPlan> future = craftingService.beginCraftingCalculation(player.level(),
                    () -> actionSource, key, amount, CalculationStrategy.REPORT_MISSING_ITEMS);
            pending.add(new PendingCraft(amount, future, request.getHoverName()));
        }
        if (pending.isEmpty()) {
            return Component.translatable("message.matter_manipulator.plan.auto_no_craftables", skipped);
        }

        PendingCraftBatch previous = PENDING_CRAFTS.put(player.getUUID(),
                new PendingCraftBatch(downlink, pending, skipped));
        if (previous != null) {
            previous.cancel();
        }
        return Component.translatable("message.matter_manipulator.plan.auto_started",
                pending.size(), requestedTotal, skipped);
    }

    public static boolean hasGridNode(ServerPlayer player, MarkedPosition downlink) {
        return exposedNode(player, downlink) != null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING_CRAFTS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<UUID, PendingCraftBatch>> batches = PENDING_CRAFTS.entrySet().iterator();
        while (batches.hasNext()) {
            Map.Entry<UUID, PendingCraftBatch> entry = batches.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            PendingCraftBatch batch = entry.getValue();
            if (player == null) {
                batch.cancel();
                batches.remove();
                continue;
            }
            batch.tick(player);
            if (batch.finished()) {
                player.displayClientMessage(Component.translatable(
                        "message.matter_manipulator.plan.auto_finished",
                        batch.submitted, batch.failed, batch.skipped), true);
                batches.remove();
            }
        }
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

    private static IActionSource actionSource(ServerPlayer player, IGridNode node) {
        return IActionSource.ofPlayer(player, () -> node);
    }

    private record PendingCraft(long amount, Future<ICraftingPlan> future, Component name) {}

    private static final class PendingCraftBatch {
        private final MarkedPosition downlink;
        private final List<PendingCraft> crafts;
        private final int skipped;
        private int submitted;
        private int failed;

        private PendingCraftBatch(MarkedPosition downlink, List<PendingCraft> crafts, int skipped) {
            this.downlink = downlink;
            this.crafts = crafts;
            this.skipped = skipped;
        }

        private void tick(ServerPlayer player) {
            IGridNode node = exposedNode(player, downlink);
            if (node == null || !node.isOnline()) {
                failRemaining(player, "message.matter_manipulator.plan.auto_failed_me_offline");
                return;
            }
            ICraftingService craftingService = node.getGrid().getService(ICraftingService.class);
            if (craftingService == null) {
                failRemaining(player, "message.matter_manipulator.plan.auto_failed_no_service");
                return;
            }
            IActionSource actionSource = actionSource(player, node);
            Iterator<PendingCraft> iterator = crafts.iterator();
            while (iterator.hasNext()) {
                PendingCraft craft = iterator.next();
                if (!craft.future.isDone()) {
                    continue;
                }
                try {
                    ICraftingPlan plan = craft.future.get();
                    if (plan.simulation() || !plan.missingItems().isEmpty()) {
                        failed++;
                        player.displayClientMessage(Component.translatable(
                                "message.matter_manipulator.plan.auto_job_missing",
                                craft.name, craft.amount), true);
                    } else {
                        ICraftingSubmitResult result = craftingService.submitJob(plan, null, null, true, actionSource);
                        if (result.successful()) {
                            submitted++;
                        } else {
                            failed++;
                            player.displayClientMessage(Component.translatable(
                                    "message.matter_manipulator.plan.auto_job_failed",
                                    craft.name, craft.amount, String.valueOf(result.errorCode())), true);
                        }
                    }
                } catch (Exception exception) {
                    failed++;
                    player.displayClientMessage(Component.translatable(
                            "message.matter_manipulator.plan.auto_job_error",
                            craft.name, craft.amount), true);
                }
                iterator.remove();
            }
        }

        private void failRemaining(ServerPlayer player, String messageKey) {
            failed += crafts.size();
            cancel();
            crafts.clear();
            player.displayClientMessage(Component.translatable(messageKey), true);
        }

        private boolean finished() {
            return crafts.isEmpty();
        }

        private void cancel() {
            for (PendingCraft craft : crafts) {
                craft.future.cancel(true);
            }
        }
    }
}
