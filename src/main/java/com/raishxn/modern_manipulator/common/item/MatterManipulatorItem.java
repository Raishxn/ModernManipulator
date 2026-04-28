package com.raishxn.modern_manipulator.common.item;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.item.component.ElectricStats;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;

import com.raishxn.modern_manipulator.common.item.MMState.MarkedPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class MatterManipulatorItem extends Item {

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
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_a", state.coordA());
        appendCoordTooltip(tooltip, "tooltip.matter_manipulator.coord_b", state.coordB());
        appendSelectionTooltip(state, tooltip);
        appendUpgradeTooltip(state, tooltip);
        appendCapabilityTooltip(state, tooltip);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();
        if (!level.isClientSide && context.getPlayer() != null) {
            MMState state = getState(stack);
            MarkedPosition markedPosition = new MarkedPosition(level.dimension().location(), context.getClickedPos());
            boolean setCoordB = context.getPlayer().isShiftKeyDown();
            if (setCoordB) {
                state.setCoordB(markedPosition);
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
                                    "message.matter_manipulator.coord_a_set",
                            markedPosition.shortText(), selectionInfo), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
}
