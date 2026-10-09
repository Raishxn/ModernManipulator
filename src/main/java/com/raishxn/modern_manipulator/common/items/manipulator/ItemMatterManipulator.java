package com.raishxn.modern_manipulator.common.items.manipulator;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendErrorToPlayer;
import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendInfoToPlayer;

import com.raishxn.modern_manipulator.CommonProxy;
import com.raishxn.modern_manipulator.GlobalMMConfig.BuildingConfig;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.IBuildable;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.building.PendingBuild;
import com.raishxn.modern_manipulator.common.building.PendingMove;
import com.raishxn.modern_manipulator.common.compat.CableHandlers;
import com.raishxn.modern_manipulator.common.data.WeightedSpecList;
import com.raishxn.modern_manipulator.common.items.MMUpgrades;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PendingAction;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.networking.Messages;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.capability.ElectricItem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.MapMaker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ItemMatterManipulator extends Item {

    public final ManipulatorTier tier;

    public ItemMatterManipulator(ManipulatorTier tier) {
        super(new Item.Properties().stacksTo(1).rarity(switch (tier) {
            case Tier0 -> Rarity.COMMON;
            case Tier1 -> Rarity.UNCOMMON;
            case Tier2 -> Rarity.RARE;
            case Tier3 -> Rarity.EPIC;
        }));

        this.tier = tier;
    }

    private static int counter = 0;
    public static final int CONNECTS_TO_AE = 0b1 << counter++;
    public static final int CONNECTS_TO_UPLINK = 0b1 << counter++;
    public static final int ALLOW_REMOVING = 0b1 << counter++;
    public static final int ALLOW_GEOMETRY = 0b1 << counter++;
    public static final int ALLOW_CONFIGURING = 0b1 << counter++;
    public static final int ALLOW_COPYING = 0b1 << counter++;
    public static final int ALLOW_EXCHANGING = 0b1 << counter++;
    public static final int ALLOW_MOVING = 0b1 << counter++;
    public static final int ALLOW_CABLES = 0b1 << counter++;
    public static final int ALLOW_SMART_COPY = 0b1 << counter++;

    public static final int ALL_MODES = ALLOW_GEOMETRY | ALLOW_COPYING | ALLOW_EXCHANGING | ALLOW_MOVING | ALLOW_CABLES;

    public enum ManipulatorTier {

        // spotless:off
        Tier0(
            32,
            16, 20,
            GTValues.HV,
            10_000_000L,
            ALLOW_GEOMETRY,
            ImmutableList.of(MMUpgrades.Mining, MMUpgrades.Speed, MMUpgrades.PowerEff)
        ),
        Tier1(
            64,
            32, 10,
            GTValues.IV,
            100_000_000L,
            ALLOW_GEOMETRY | CONNECTS_TO_AE | ALLOW_REMOVING | ALLOW_EXCHANGING | ALLOW_CONFIGURING | ALLOW_CABLES,
            ImmutableList.of(MMUpgrades.Speed, MMUpgrades.PowerEff)
        ),
        Tier2(
            128,
            64, 5,
            GTValues.LuV,
            1_000_000_000L,
            ALLOW_GEOMETRY | CONNECTS_TO_AE | ALLOW_REMOVING | ALLOW_EXCHANGING | ALLOW_CONFIGURING | ALLOW_CABLES | ALLOW_COPYING | ALLOW_MOVING,
            ImmutableList.of(MMUpgrades.Speed, MMUpgrades.PowerEff)
        ),
        Tier3(
            -1,
            -1, 5,
            GTValues.ZPM,
            10_000_000_000L,
            ALLOW_GEOMETRY | CONNECTS_TO_AE | ALLOW_REMOVING | ALLOW_EXCHANGING | ALLOW_CONFIGURING | ALLOW_CABLES | ALLOW_COPYING | ALLOW_MOVING | CONNECTS_TO_UPLINK | ALLOW_SMART_COPY,
            ImmutableList.of(MMUpgrades.PowerEff, MMUpgrades.PowerP2P)
        );
        // spotless:on

        public final int tier = ordinal();
        public final int maxRange;
        private final int placeSpeed;
        public final int placeTicks;
        public final int voltageTier;
        public final long maxCharge;
        public final int capabilities;
        public final Set<MMUpgrades> allowedUpgrades;

        ManipulatorTier(int maxRange, int placeSpeed, int placeTicks, int voltageTier, long maxCharge, int capabilities,
                        List<MMUpgrades> allowedUpgrades) {
            this.maxRange = maxRange;
            this.placeSpeed = placeSpeed;
            this.placeTicks = placeTicks;
            this.voltageTier = voltageTier;
            this.maxCharge = maxCharge;
            this.capabilities = capabilities;
            this.allowedUpgrades = Collections.unmodifiableSet(allowedUpgrades.isEmpty() ? EnumSet.noneOf(MMUpgrades.class) :
                EnumSet.copyOf(allowedUpgrades));
        }

        /** The amount of blocks placed per place tick. MK3's speed is configurable. */
        public int getPlaceSpeed() {
            return placeSpeed == -1 ? BuildingConfig.mk3BlocksPerPlace() : placeSpeed;
        }
    }

    // #region Energy

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return new ICapabilityProvider() {

            private final LazyOptional<IElectricItem> electricItem = LazyOptional.of(() -> new ElectricItem(stack, tier.maxCharge,
                tier.voltageTier, true, false) {

                @Override
                public long getTransferLimit() {
                    return GTValues.V[ItemMatterManipulator.this.tier.voltageTier] * 16;
                }
            });

            @Override
            public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
                return GTCapability.CAPABILITY_ELECTRIC_ITEM.orEmpty(cap, electricItem);
            }
        };
    }

    public long getCharge(ItemStack stack) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);

        return electricItem == null ? 0 : electricItem.getCharge();
    }

    public long getMaxCharge(ItemStack stack) {
        return tier.maxCharge;
    }

    public long charge(ItemStack stack, long amount) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);

        return electricItem == null ? 0 : electricItem.charge(amount, Integer.MAX_VALUE, true, false);
    }

    public ItemStack createChargedStack() {
        ItemStack stack = getDefaultInstance();
        charge(stack, tier.maxCharge);
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = new ItemStack(this, 1);
        setState(stack, new MMState());
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * getCharge(stack) / tier.maxCharge);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x00BFFF;
    }

    public void refillPower(ItemStack stack, MMState state) {
        if (!state.hasUpgrade(MMUpgrades.PowerP2P)) return;
        if (!state.connectToUplink()) return;

        long toFill = getMaxCharge(stack) - getCharge(stack);

        if (toFill <= 0) return;

        double drained = state.uplink.drainPower(toFill);

        charge(stack, (long) drained);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level worldIn, Entity entityIn, int slot, boolean selected) {
        if (worldIn.isClientSide) return;

        if (worldIn.getGameTime() % 100 == 0) {
            MMState state = getState(stack);

            refillPower(stack, state);
        }
    }

    // #endregion

    public static MMState getState(ItemStack itemStack) {
        MMState state = MMState.load(itemStack.getTag());

        state.manipulator = (ItemMatterManipulator) itemStack.getItem();

        return state;
    }

    public static void setState(ItemStack itemStack, MMState state) {
        itemStack.getOrCreateTag().putString(MMState.TAG_KEY, state.save());
    }

    // this is super cursed but doing it properly would take a ton of effort for no real gain
    private static boolean ttAEWorks, ttUplinkWorks;

    public static void onTooltipResponse(int state) {
        ttAEWorks = (state & MMUtils.TOOLTIP_AE_WORKS) != 0;
        ttUplinkWorks = (state & MMUtils.TOOLTIP_UPLINK_WORKS) != 0;
    }

    private long lastTooltipQueryMS;

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack itemStack, @Nullable Level level, List<Component> desc, TooltipFlag flag) {
        MMState state = getState(itemStack);

        // spotless:off
        if (!Screen.hasShiftDown()) {
            desc.add(Component.translatable("mm.tooltip.hold_shift"));
        } else {
            if (state.hasCap(CONNECTS_TO_AE) || state.hasCap(CONNECTS_TO_UPLINK)) {
                long time = System.currentTimeMillis();

                if ((time - lastTooltipQueryMS) > 1000) {
                    lastTooltipQueryMS = time;

                    Player player = CommonProxy.getClientPlayer();

                    if (player != null) {
                        int slot = -1;
                        AbstractContainerMenu c = player.containerMenu;

                        for (int i = 0; i < c.slots.size(); i++) {
                            if (c.getSlot(i).getItem() == itemStack) {
                                slot = i;
                                break;
                            }
                        }

                        if (slot != -1) {
                            Messages.TooltipQuery.sendToServer(slot);
                        }
                    }
                }
            }

            if (state.hasCap(CONNECTS_TO_AE)) {
                if (state.meLink != null) {
                    if (ttAEWorks) {
                        desc.add(Component.translatable("mm.tooltip.me_conn.can_interact"));
                    } else {
                        desc.add(Component.translatable("mm.tooltip.me_conn.cannot_interact"));
                    }
                } else {
                    desc.add(Component.translatable("mm.tooltip.me_conn.no_conn"));
                }
            }

            if (state.hasCap(CONNECTS_TO_UPLINK)) {
                if (state.uplinkAddress != null) {
                    if (ttUplinkWorks) {
                        desc.add(Component.translatable("mm.tooltip.uplink_conn.can_interact"));
                    } else {
                        desc.add(Component.translatable("mm.tooltip.uplink_conn.cannot_interact"));
                    }
                    addInfoLine(desc, "mm.tooltip.uplink_conn.address", state.uplinkAddress, Long::toHexString);
                } else {
                    desc.add(Component.translatable("mm.tooltip.uplink_conn.no_conn"));
                }
            }

            if (state.config.action != null) {
                addInfoLine(desc, "mm.tooltip.pending_action", Component.translatable(switch (state.config.action) {
                    case MOVING_COORDS -> "mm.tooltip.pending_action.moving_coords";
                    case GEOM_SELECTING_BLOCK -> "mm.tooltip.pending_action.geom_selecting_block";
                    case MARK_COPY_A -> "mm.tooltip.pending_action.mark_copy_a";
                    case MARK_COPY_B -> "mm.tooltip.pending_action.mark_copy_b";
                    case MARK_CUT_A -> "mm.tooltip.pending_action.mark_cut_a";
                    case MARK_CUT_B -> "mm.tooltip.pending_action.mark_cut_b";
                    case MARK_PASTE -> "mm.tooltip.pending_action.mark_paste";
                    case EXCH_ADD_REPLACE -> "mm.tooltip.pending_action.exch_add_replace";
                    case EXCH_SET_REPLACE -> "mm.tooltip.pending_action.exch_set_replace";
                    case EXCH_SET_TARGET -> "mm.tooltip.pending_action.exch_set_target";
                    case PICK_CABLE -> "mm.tooltip.pending_action.pick_cable";
                    case MARK_ARRAY -> "mm.tooltip.pending_action.mark_array";
                }).getString());
            }

            if (Integer.bitCount(tier.capabilities & ALL_MODES) > 1) {
                addInfoLine(desc, "mm.tooltip.mode", Component.translatable(switch (state.config.placeMode) {
                    case GEOMETRY -> "mm.tooltip.mode.geometry";
                    case MOVING -> "mm.tooltip.mode.moving";
                    case COPYING -> "mm.tooltip.mode.copying";
                    case EXCHANGING -> "mm.tooltip.mode.exchanging";
                    case CABLES -> "mm.tooltip.mode.cables";
                }).getString());
            }

            if (state.hasCap(ALLOW_REMOVING)) {
                addInfoLine(desc, "mm.tooltip.removing", Component.translatable(switch (state.config.removeMode) {
                    case ALL -> "mm.tooltip.removing.all";
                    case REPLACEABLE -> "mm.tooltip.removing.replaceable";
                    case NONE -> "mm.tooltip.removing.none";
                }).getString());
            }

            if (state.config.placeMode == PlaceMode.GEOMETRY) {
                addInfoLine(desc, "mm.tooltip.shape", Component.translatable(switch (state.config.shape) {
                    case LINE -> "mm.tooltip.shape.line";
                    case CUBE -> "mm.tooltip.shape.cube";
                    case SPHERE -> "mm.tooltip.shape.sphere";
                    case CYLINDER -> "mm.tooltip.shape.cylinder";
                }).getString());

                addInfoLine(desc, "mm.tooltip.coord_a", state.config.coordA);
                addInfoLine(desc, "mm.tooltip.coord_b", state.config.coordB);

                addInfoLine(desc, "mm.tooltip.corner_block", state.config.corners);
                addInfoLine(desc, "mm.tooltip.edge_block", state.config.edges);
                addInfoLine(desc, "mm.tooltip.face_block", state.config.faces);
                addInfoLine(desc, "mm.tooltip.volume_block", state.config.volumes);
            }

            if (state.config.placeMode == PlaceMode.COPYING) {
                addInfoLine(desc, "mm.tooltip.copying.copy_a", state.config.coordA);
                addInfoLine(desc, "mm.tooltip.copying.copy_b", state.config.coordB);

                addInfoLine(desc, "mm.tooltip.paste", state.config.coordC);

                addInfoLine(desc,
                    "mm.tooltip.copying.stack",
                    state.config.arraySpan,
                    span -> String.format(
                        "X: %dx, Y: %dx, Z: %dx",
                        span.x + (span.x < 0 ? -1 : 1),
                        span.y + (span.y < 0 ? -1 : 1),
                        span.z + (span.z < 0 ? -1 : 1)));

                addInfoLine(desc, "mm.tooltip.copying.wireless_link", state.config.linkExternalHubs,
                    on -> Component.translatable(on ? "mm.gui.smart_copy.on" : "mm.gui.smart_copy.off").getString());

                if (state.hasCap(ALLOW_SMART_COPY)) {
                    addInfoLine(desc, "mm.tooltip.copying.auto_proxy_cribs", state.config.replaceCribsWithProxies,
                        on -> Component.translatable(on ? "mm.gui.smart_copy.on" : "mm.gui.smart_copy.off").getString());
                }

                addInfoLine(desc, "mm.tooltip.copying.auto_p2p_interfaces", state.config.replaceInterfacesWithP2P,
                    on -> Component.translatable(on ? "mm.gui.smart_copy.on" : "mm.gui.smart_copy.off").getString());
            }

            if (state.config.placeMode == PlaceMode.MOVING) {
                addInfoLine(desc, "mm.tooltip.moving.cut_a", state.config.coordA);
                addInfoLine(desc, "mm.tooltip.moving.cut_b", state.config.coordB);

                addInfoLine(desc, "mm.tooltip.paste", state.config.coordC);
            }

            if (state.config.placeMode == PlaceMode.EXCHANGING) {
                addInfoLine(desc, "mm.tooltip.exchanging.removable", state.config.replaceWhitelist);
                addInfoLine(desc, "mm.tooltip.exchanging.replacing", state.config.replaceWith);
            }

            if (state.config.placeMode == PlaceMode.CABLES) {
                addInfoLine(desc, "mm.tooltip.coord_a", state.config.coordA);
                addInfoLine(desc, "mm.tooltip.coord_b", state.config.coordB);

                addInfoLine(desc, "mm.tooltip.cable", state.config.cables, BlockSpec::toDisplayString);
            }

            List<MMUpgrades> upgrades = new ArrayList<>(state.getInstalledUpgrades());
            upgrades.sort(Comparator.comparingInt(Enum::ordinal));

            if (!upgrades.isEmpty()) {
                desc.add(Component.translatable("mm.tooltip.installed_upgrades"));

                for (MMUpgrades upgrade : upgrades) {
                    desc.add(Component.literal("- ").append(upgrade.getStack().getHoverName()));
                }
            }
        }

        desc.add(
            Component.translatable(
                "mm.tooltip.voltage",
                MMUtils.formatNumber(MMUtils.clamp(getCharge(itemStack), 0, tier.maxCharge)),
                MMUtils.formatNumber(tier.maxCharge),
                MMUtils.formatNumber(GTValues.V[tier.voltageTier]))
                .withStyle(ChatFormatting.AQUA));

        // spotless:on
    }

    private <T> void addInfoLine(List<Component> desc, String formatKey, T value) {
        addInfoLine(desc, formatKey, value, T::toString);
    }

    private <T> void addInfoLine(List<Component> desc, String formatKey, T value, Function<T, String> toString) {
        MutableComponent arg;

        if (value != null) {
            arg = Component.literal(toString.apply(value)).withStyle(ChatFormatting.BLUE);
        } else {
            arg = Component.translatable("mm.tooltip.none").withStyle(ChatFormatting.GRAY);
        }

        desc.add(Component.translatable(formatKey, arg));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (player.isUsingItem()) return InteractionResultHolder.pass(stack);

        MMState state = getState(stack);

        if (state.config.action != null) {
            BlockHitResult hit = MMUtils.getHitResult(player, true);

            if (handleAction(stack, world, player, state, hit)) {
                setState(stack, state);

                return InteractionResultHolder.success(stack);
            }
        }

        BlockHitResult hit = MMUtils.getHitResult(player, false);

        if (hit != null) {
            BlockPos pos = hit.getBlockPos();
            Location location = new Location(world, pos.getX(), pos.getY(), pos.getZ());

            if (!player.isShiftKeyDown()) {
                location.offset(hit.getDirection());
            }

            if (state.config.placeMode == PlaceMode.GEOMETRY || state.config.placeMode == PlaceMode.EXCHANGING ||
                state.config.placeMode == PlaceMode.CABLES) {
                state.config.coordA = location;
                state.config.coordB = null;
                state.config.coordC = null;
                state.config.coordBOffset = new Vector3i();
                state.config.action = PendingAction.MOVING_COORDS;
            }

            setState(stack, state);

            return InteractionResultHolder.success(stack);
        } else {
            if (player.isShiftKeyDown()) {
                player.startUsingItem(hand);
            } else if (world.isClientSide) {
                CommonProxy.openRadialMenu(player, stack);
            }

            return InteractionResultHolder.consume(stack);
        }
    }

    /**
     * Handles the pending action. Responsible for clearing the action afterwards.
     *
     * @return True when the action was successfully handled. Treated as a no-op when false.
     */
    public boolean handleAction(ItemStack itemStack, Level world, Player player, MMState state, @Nullable BlockHitResult hit) {
        switch (state.config.action) {
            case MOVING_COORDS -> {
                Vector3i lookingAt = MMUtils.getLookingAtLocation(player);

                if (state.config.placeMode == PlaceMode.GEOMETRY && state.config.coordAOffset == null && state.config.coordBOffset != null &&
                    state.config.coordCOffset == null && state.config.shape.requiresC()) {
                    state.config.coordA = state.config.getCoordA(world, lookingAt);
                    state.config.coordB = state.config.getCoordB(world, lookingAt);
                    state.config.coordC = null;
                    state.config.coordAOffset = null;
                    state.config.coordBOffset = null;
                    state.config.coordCOffset = new Vector3i();
                } else {
                    state.config.coordA = state.config.getCoordA(world, lookingAt);
                    state.config.coordB = state.config.getCoordB(world, lookingAt);
                    state.config.coordC = state.config.getCoordC(world, lookingAt);
                    state.config.coordAOffset = null;
                    state.config.coordBOffset = null;
                    state.config.coordCOffset = null;
                    state.config.action = null;
                }

                return true;
            }
            case GEOM_SELECTING_BLOCK -> {
                state.config.action = null;

                onPickBlock(world, player, itemStack, state, hit, player.isShiftKeyDown(), null);

                return true;
            }
            case MARK_COPY_A -> {
                state.config.coordA = new Location(world, MMUtils.getLookingAtLocation(player));
                state.config.action = PendingAction.MARK_COPY_B;
                return true;
            }
            case MARK_COPY_B -> {
                state.config.coordB = new Location(world, MMUtils.getLookingAtLocation(player));
                state.config.action = null;
                return true;
            }
            case MARK_CUT_A -> {
                state.config.coordA = new Location(world, MMUtils.getLookingAtLocation(player));
                state.config.action = PendingAction.MARK_CUT_B;
                return true;
            }
            case MARK_CUT_B -> {
                state.config.coordB = new Location(world, MMUtils.getLookingAtLocation(player));
                state.config.action = null;
                return true;
            }
            case MARK_PASTE -> {
                state.config.coordC = new Location(world, MMUtils.getLookingAtLocation(player));
                state.config.action = null;
                return true;
            }
            case EXCH_SET_TARGET -> {
                onExchangeSetTarget(world, player, itemStack, state, hit, null);
                state.config.action = null;
                return true;
            }
            case EXCH_ADD_REPLACE -> {
                onExchangeAddWhitelist(world, player, itemStack, state, hit);
                state.config.action = null;
                return true;
            }
            case EXCH_SET_REPLACE -> {
                onExchangeSetWhitelist(world, player, itemStack, state, hit, null);
                state.config.action = null;
                return true;
            }
            case PICK_CABLE -> {
                onPickCable(world, player, itemStack, state, hit, null);
                state.config.action = null;
                return true;
            }
            case MARK_ARRAY -> {
                onMarkArray(world, player, itemStack, state);
                state.config.action = null;
                return true;
            }
        }

        return false;
    }

    public void onMMBPressed(Player player, ItemStack stack, MMState state) {
        Level world = player.level();

        if (state.config.placeMode == PlaceMode.GEOMETRY) {
            onPickBlock(world, player, stack, state, MMUtils.getHitResult(player, true), player.isShiftKeyDown(), null);
        }
        if (state.config.placeMode == PlaceMode.EXCHANGING) {
            if (player.isShiftKeyDown()) {
                onExchangeSetWhitelist(world, player, stack, state, MMUtils.getHitResult(player, true), null);
            } else {
                onExchangeSetTarget(world, player, stack, state, MMUtils.getHitResult(player, true), null);
            }
        }
        if (state.config.placeMode == PlaceMode.CABLES) {
            onPickCable(world, player, stack, state, MMUtils.getHitResult(player, true), null);
        }
    }

    public static void onMMBPressedInGUI(Player player, ItemStack stack, MMState state, final boolean isSneaking, ItemStack hoveredStack) {
        BlockSpec block = BlockSpec.fromStack(null, hoveredStack);
        Level world = player.level();

        if (state.config.placeMode == PlaceMode.GEOMETRY) {
            onPickBlock(world, player, stack, state, null, isSneaking, block);
        }
        if (state.config.placeMode == PlaceMode.EXCHANGING) {
            if (isSneaking) {
                onExchangeSetWhitelist(world, player, stack, state, null, block);
            } else {
                onExchangeSetTarget(world, player, stack, state, null, block);
            }
        }
        if (state.config.placeMode == PlaceMode.CABLES) {
            onPickCable(world, player, stack, state, null, block);
        }
    }

    private static void onPickBlock(Level world, Player player, ItemStack stack, MMState state, @Nullable BlockHitResult hit,
                                    final boolean add, @Nullable BlockSpec block) {
        if (block == null) block = BlockSpec.fromPickBlock(world, player, hit);

        String whatKey = null;

        switch (state.config.blockSelectMode) {
            case CORNERS -> {
                if (state.config.corners == null || !add) state.config.corners = new WeightedSpecList();
                state.config.corners.add(block);
                whatKey = "mm.enum.what.corners";
            }
            case EDGES -> {
                if (state.config.edges == null || !add) state.config.edges = new WeightedSpecList();
                state.config.edges.add(block);
                whatKey = "mm.enum.what.edges";
            }
            case FACES -> {
                if (state.config.faces == null || !add) state.config.faces = new WeightedSpecList();
                state.config.faces.add(block);
                whatKey = "mm.enum.what.faces";
            }
            case VOLUMES -> {
                if (state.config.volumes == null || !add) state.config.volumes = new WeightedSpecList();
                state.config.volumes.add(block);
                whatKey = "mm.enum.what.volumes";
            }
            case ALL -> {
                if (state.config.corners == null || !add) state.config.corners = new WeightedSpecList();
                if (state.config.edges == null || !add) state.config.edges = new WeightedSpecList();
                if (state.config.faces == null || !add) state.config.faces = new WeightedSpecList();
                if (state.config.volumes == null || !add) state.config.volumes = new WeightedSpecList();
                state.config.corners.add(block);
                state.config.edges.add(block);
                state.config.faces.add(block);
                state.config.volumes.add(block);
                whatKey = "mm.enum.what.all";
            }
        }

        if (add) {
            sendInfoToPlayer(player, "mm.info.added", block.getChatComponent(), Component.translatable(whatKey));
        } else {
            sendInfoToPlayer(player, "mm.info.set", Component.translatable(whatKey), block.getChatComponent());
        }
    }

    private static void onExchangeSetTarget(Level world, Player player, ItemStack stack, MMState state, @Nullable BlockHitResult hit,
                                            @Nullable BlockSpec block) {
        if (block == null) block = BlockSpec.fromPickBlock(world, player, hit);

        if (hit != null) checkForCables(state, block, world, hit.getBlockPos());

        state.config.replaceWith = new WeightedSpecList();
        state.config.replaceWith.add(block);

        sendInfoToPlayer(player, "mm.info.set_block_to_replace_with", block.getChatComponent());
    }

    private void onExchangeAddWhitelist(Level world, Player player, ItemStack stack, MMState state, @Nullable BlockHitResult hit) {
        BlockSpec block = BlockSpec.fromPickBlock(world, player, hit);

        if (hit != null) checkForCables(state, block, world, hit.getBlockPos());

        if (state.config.replaceWhitelist == null) {
            state.config.replaceWhitelist = new WeightedSpecList();
        }

        state.config.replaceWhitelist.add(block);

        sendInfoToPlayer(player, "mm.info.added_block_to_exchange_whitelist", block.getChatComponent());
    }

    private static void onExchangeSetWhitelist(Level world, Player player, ItemStack stack, MMState state, @Nullable BlockHitResult hit,
                                               @Nullable BlockSpec block) {
        if (block == null) block = BlockSpec.fromPickBlock(world, player, hit);

        if (hit != null) checkForCables(state, block, world, hit.getBlockPos());

        state.config.replaceWhitelist = new WeightedSpecList();
        state.config.replaceWhitelist.add(block);

        sendInfoToPlayer(player, "mm.info.set_exchange_whitelist_to_only_contain", block.getChatComponent());
    }

    private static void onPickCable(Level world, Player player, ItemStack stack, MMState state, @Nullable BlockHitResult hit,
                                    @Nullable BlockSpec cable) {
        if (cable == null) cable = new BlockSpec();

        if (hit != null) {
            CableHandlers.pickCable(cable, world, hit.getBlockPos());
        }

        state.config.cables = cable.isAir() ? null : cable;

        sendInfoToPlayer(player, "mm.info.set_cable", cable.getChatComponent());
    }

    private static void checkForCables(MMState state, BlockSpec spec, Level world, BlockPos pos) {
        if (state.hasCap(ALLOW_CABLES)) {
            CableHandlers.getCableInWorld(spec, world, pos);
        }
    }

    private void onMarkArray(Level world, Player player, ItemStack stack, MMState state) {
        Vector3i lookingAt = MMUtils.getLookingAtLocation(player);

        if (!Location.areCompatible(state.config.coordA, state.config.coordB)) {
            sendErrorToPlayer(player, "mm.info.error.cannot_mark_copy");
            state.config.arraySpan = null;
            return;
        }

        if (state.config.coordC == null || !state.config.coordC.isInWorld(world)) {
            sendErrorToPlayer(player, "mm.info.error.cannot_mark_paste");
            state.config.arraySpan = null;
            return;
        }

        state.config.arraySpan = state.config.getArrayMult(world, state.config.coordA, state.config.coordB, state.config.coordC, lookingAt);
    }

    /**
     * A weak-keyed map containing every pending build.
     * Entries are added when the player starts holding shift+right click.
     * Entries are removed once the player stops holding shift+right click.
     */
    static final Map<Player, IBuildable> PENDING_BUILDS = new MapMaker().weakKeys().makeMap();

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return Integer.MAX_VALUE;
    }

    public static void stopBuildable(Player player) {
        if (!player.level().isClientSide) {
            IBuildable buildable = PENDING_BUILDS.remove(player);

            if (buildable != null) buildable.onStopped();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player) stopBuildable(player);
    }

    @Override
    public void onUseTick(Level world, LivingEntity entity, ItemStack stack, int count) {
        if (world.isClientSide || !(entity instanceof Player player)) return;

        int ticksUsed = Integer.MAX_VALUE - count;

        MMState state = getState(stack);

        if (ticksUsed == 1) {
            switch (state.config.placeMode) {
                case GEOMETRY, COPYING, EXCHANGING, CABLES -> PENDING_BUILDS.put(player, getPendingBuild(player, stack, state));
                case MOVING -> PENDING_BUILDS.put(player, getPendingMove(player, stack, state));
            }
        }

        int placeTicks = tier.placeTicks;

        if (state.hasUpgrade(MMUpgrades.Speed)) {
            placeTicks = placeTicks / 2;
        }

        if (ticksUsed >= 10 && (ticksUsed % placeTicks) == 0) {
            try {
                IBuildable buildable = PENDING_BUILDS.get(player);

                if (buildable != null) buildable.tryPlaceBlocks(stack, player);
            } catch (Throwable t) {
                ModernManipulator.LOG.error("Could not place blocks", t);
                sendErrorToPlayer(player, "mm.info.error.could_not_place_blocks");
            }
        }
    }

    /**
     * Prevents the item from being re-equipped (and stopping its use) when only its charge or state changes.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }

    private IBuildable getPendingBuild(Player player, ItemStack stack, MMState state) {
        List<PendingBlock> blocks = state.getPendingBlocks(tier, player.level());

        if (tier.maxRange != -1) {
            int maxRange2 = tier.maxRange * tier.maxRange;

            Location playerLocation = new Location(player.level(), Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()));

            blocks.removeIf(block -> block.distanceTo2(playerLocation) > maxRange2);
        }

        blocks.sort(PendingBlock.getComparator());

        return new PendingBuild(player, state, tier, blocks);
    }

    private IBuildable getPendingMove(Player player, ItemStack stack, MMState state) {
        return new PendingMove(player, state, tier);
    }

    public void setMELink(ItemStack stack, @Nullable Location link) {
        MMState state = getState(stack);

        state.meLink = state.hasCap(CONNECTS_TO_AE) ? link : null;

        setState(stack, state);
    }

    public void setUplinkAddress(ItemStack stack, Long address) {
        MMState state = getState(stack);

        if (state.hasCap(CONNECTS_TO_UPLINK)) {
            state.uplinkAddress = address;

            setState(stack, state);
        }
    }
}
