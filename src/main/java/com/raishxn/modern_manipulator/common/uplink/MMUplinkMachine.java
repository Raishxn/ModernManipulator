package com.raishxn.modern_manipulator.common.uplink;

import static com.raishxn.modern_manipulator.common.utils.MMUtils.sendInfoToPlayer;

import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.networking.Messages;
import com.raishxn.modern_manipulator.common.utils.BigFluidStack;
import com.raishxn.modern_manipulator.common.utils.BigItemStack;
import com.raishxn.modern_manipulator.common.utils.ItemId;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Matter Manipulator Quantum Uplink.
 * Interdimensional and infinite range uplink for matter manipulators. Connects to an ME system via its ME connector
 * hatch, consumes 1A ZPM while active and plasma (from input hatches) for every transfer.
 */
public class MMUplinkMachine extends WorkableElectricMultiblockMachine implements IUplinkMulti, IInteractedMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(MMUplinkMachine.class,
        WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    public static final long BASE_PLASMA_EU_COST = 131_072;

    @Persisted
    private long pendingPlasmaEU = 0;
    @Persisted
    private long address = 0;

    private final List<MMUplinkMEHatchPartMachine> uplinkHatches = new ArrayList<>();
    private final List<FluidHatchPartMachine> inputHatches = new ArrayList<>();
    private IEnergyContainer energyInput = new EnergyContainerList(new ArrayList<>());

    private TickableSubscription tickSubscription;

    private boolean active = false;

    private UplinkState lastState;
    private int stateCounter = 0;

    /** The client side state */
    private UplinkState clientState = UplinkState.OFF;

    public MMUplinkMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (address == 0) address = newAddress();

        if (getLevel() instanceof ServerLevel) {
            tickSubscription = subscribeServerTick(this::uplinkTick);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        IUplinkMulti.unregisterUplink(address, this);
    }

    private static long newAddress() {
        return (long) (Long.MAX_VALUE * Math.random());
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();

        uplinkHatches.clear();
        inputHatches.clear();

        List<IEnergyContainer> energy = new ArrayList<>();

        for (IMultiPart part : getParts()) {
            if (part instanceof MMUplinkMEHatchPartMachine hatch) uplinkHatches.add(hatch);
            if (part instanceof FluidHatchPartMachine hatch && hatch.tank.getHandlerIO() == IO.IN) inputHatches.add(hatch);

            for (var handlerList : part.getRecipeHandlers()) {
                if (!handlerList.getHandlerIO().support(IO.IN)) continue;

                handlerList.getCapability(EURecipeCapability.CAP).stream()
                    .filter(IEnergyContainer.class::isInstance)
                    .map(IEnergyContainer.class::cast)
                    .forEach(energy::add);
            }
        }

        energyInput = new EnergyContainerList(energy);
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();

        uplinkHatches.clear();
        inputHatches.clear();
        energyInput = new EnergyContainerList(new ArrayList<>());

        setActive(false);
    }

    private void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;

            if (active) {
                IUplinkMulti.registerUplink(address, this);
            } else {
                IUplinkMulti.unregisterUplink(address, this);
            }

            sendUplinkStateUpdate();
        }

        getRecipeLogic().setStatus(active ? RecipeLogic.Status.WORKING : RecipeLogic.Status.IDLE);
    }

    private void uplinkTick() {
        long cost = GTValues.V[GTValues.ZPM];

        boolean canRun = isFormed() && isWorkingEnabled() && energyInput.getEnergyStored() >= cost;

        if (canRun) {
            energyInput.removeEnergy(cost);
        }

        setActive(canRun);

        if (getOffsetTimer() % 5 == 0) {
            UplinkState state = getState();
            stateCounter++;

            // if the state has changed or 10 seconds have passed, send an update to all nearby clients
            if (state != lastState || stateCounter > 40) {
                lastState = state;
                stateCounter = 0;
                sendUplinkStateUpdate();
            }
        }
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);

        if (held.getItem() instanceof ItemMatterManipulator manipulator) {
            if (!world.isClientSide) {
                if (ItemMatterManipulator.getState(held).hasCap(ItemMatterManipulator.CONNECTS_TO_UPLINK)) {
                    manipulator.setUplinkAddress(held, address);
                    sendInfoToPlayer(player, "mm.info.uplink_bound", Long.toHexString(address));
                } else {
                    MMUtils.sendErrorToPlayer(player, "mm.info.error.cannot_bind_uplink");
                }
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);

        textList.add(Component.translatable("mm.uplink.address", Long.toHexString(address)).withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("mm.uplink.stored_plasma", MMUtils.formatNumber(pendingPlasmaEU)).withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("mm.uplink.hatch_plasma", MMUtils.formatNumber(getPlasmaEUInHatches())).withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable(switch (getState()) {
            case OFF -> "mm.uplink.state.off";
            case IDLE -> "mm.uplink.state.idle";
            case ACTIVE -> "mm.uplink.state.active";
        }));
    }

    // #region IUplinkMulti

    @Override
    public long getAddress() {
        return address;
    }

    @Override
    public boolean isValid() {
        return !isInValid() && getLevel() != null;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public Location getLocation() {
        return new Location(getLevel(), getPos());
    }

    @Override
    public UplinkState getState() {
        if (getLevel() != null && getLevel().isClientSide) return clientState;

        if (active) {
            return uplinkHatches.stream().anyMatch(MMUplinkMEHatchPartMachine::hasAnyRequests) ? UplinkState.ACTIVE : UplinkState.IDLE;
        } else {
            return UplinkState.OFF;
        }
    }

    public void setClientState(UplinkState state) {
        this.clientState = state;
        scheduleRenderUpdate();
    }

    private void sendUplinkStateUpdate() {
        if (getLevel() == null || getLevel().isClientSide) return;

        Messages.UpdateUplinkState.sendToPlayersAround(getLocation(), Pair.of(getLocation(), getState().ordinal()));
    }

    private @Nullable MMUplinkMEHatchPartMachine getMEHatch() {
        for (MMUplinkMEHatchPartMachine hatch : uplinkHatches) {
            if (hatch != null && hatch.isActive() && hatch.isPowered()) return hatch;
        }

        return null;
    }

    @Override
    public Object getStorageIdentity() {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();
        Object grid = hatch == null ? null : hatch.getGrid();
        return grid == null ? this : grid;
    }

    @Override
    public ObjectObjectImmutablePair<UplinkStatus, List<BigItemStack>> tryConsumeItems(List<BigItemStack> requestedItems, boolean simulate,
                                                                                       boolean fuzzy) {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();

        if (hatch == null) return ObjectObjectImmutablePair.of(UplinkStatus.NO_HATCH, null);

        MEStorage storage = hatch.getStorage();

        if (storage == null) return ObjectObjectImmutablePair.of(UplinkStatus.AE_OFFLINE, null);

        List<BigItemStack> out = new ArrayList<>();

        Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;

        for (BigItemStack req : requestedItems) {
            if (req.getStackSize() == 0) continue;

            List<AEItemKey> matches = new ArrayList<>();

            if (fuzzy) {
                for (var entry : storage.getAvailableStacks()) {
                    if (entry.getKey() instanceof AEItemKey key && key.getItem() == req.getItem()) matches.add(key);
                }
            } else {
                AEItemKey key = AEItemKey.of(req.getItemStack());
                if (key != null) matches.add(key);
            }

            for (AEItemKey match : matches) {
                if (req.getStackSize() == 0) break;

                long available = storage.extract(match, req.getStackSize(), Actionable.SIMULATE, hatch.getRequestSource());

                if (available <= 0) continue;

                if (!simulate) {
                    if (!consumePlasmaEU(available * BASE_PLASMA_EU_COST)) {
                        return ObjectObjectImmutablePair.of(UplinkStatus.NO_PLASMA, out);
                    }
                }

                long extracted = storage.extract(match, available, mode, hatch.getRequestSource());

                if (extracted > 0) {
                    out.add(BigItemStack.create(ItemId.create(match.getItem(), match.getTag()), extracted));
                    req.decStackSize(extracted);
                }
            }
        }

        return ObjectObjectImmutablePair.of(UplinkStatus.OK, out);
    }

    @Override
    public UplinkStatus tryGivePlayerItems(List<BigItemStack> items) {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();

        if (hatch == null) return UplinkStatus.NO_HATCH;

        MEStorage storage = hatch.getStorage();

        if (storage == null) return UplinkStatus.AE_OFFLINE;

        for (BigItemStack item : items) {
            if (item == null) continue;

            if (!consumePlasmaEU(item.getStackSize() * BASE_PLASMA_EU_COST)) return UplinkStatus.NO_PLASMA;

            AEItemKey key = AEItemKey.of(item.getItemStack());

            if (key == null) continue;

            long inserted = storage.insert(key, item.getStackSize(), Actionable.MODULATE, hatch.getRequestSource());

            item.setStackSize(item.getStackSize() - inserted);
        }

        return UplinkStatus.OK;
    }

    @Override
    public UplinkStatus tryGivePlayerFluids(List<BigFluidStack> fluids) {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();

        if (hatch == null) return UplinkStatus.NO_HATCH;

        MEStorage storage = hatch.getStorage();

        if (storage == null) return UplinkStatus.AE_OFFLINE;

        for (BigFluidStack fluid : fluids) {
            if (fluid == null) continue;

            if (!consumePlasmaEU(MMUtils.ceilDiv(fluid.getStackSize(), 1000) * BASE_PLASMA_EU_COST)) return UplinkStatus.NO_PLASMA;

            AEKey key = AEFluidKey.of(fluid.getFluidStack());

            if (key == null) continue;

            long inserted = storage.insert(key, fluid.getStackSize(), Actionable.MODULATE, hatch.getRequestSource());

            fluid.setStackSize(fluid.getStackSize() - inserted);
        }

        return UplinkStatus.OK;
    }

    /**
     * Tries to consume plasma EU.
     * Converts plasma to EU as needed.
     */
    private boolean consumePlasmaEU(long euToConsume) {
        if (pendingPlasmaEU < euToConsume) {
            generatePlasmaEU(euToConsume - pendingPlasmaEU);
        }

        if (pendingPlasmaEU >= euToConsume) {
            pendingPlasmaEU -= euToConsume;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Converts plasma in hatches to EU.
     */
    private void generatePlasmaEU(long euToGenerate) {
        if (getLevel() == null) return;

        List<GTRecipe> fuels = getLevel().getRecipeManager().getAllRecipesFor(GTRecipeTypes.PLASMA_GENERATOR_FUELS);

        for (FluidHatchPartMachine hatch : inputHatches) {
            for (int i = 0; i < hatch.tank.getTanks(); i++) {
                FluidStack fluid = hatch.tank.getFluidInTank(i);

                if (fluid.isEmpty()) continue;

                long euPerLitre = getEUPerLitre(fuels, fluid);

                if (euPerLitre <= 0) continue;

                int litresToConsume = (int) Math.min(Integer.MAX_VALUE, MMUtils.ceilDiv(euToGenerate, euPerLitre));

                FluidStack toConsume = fluid.copy();
                toConsume.setAmount(litresToConsume);

                FluidStack drained = hatch.tank.drainInternal(toConsume, IFluidHandler.FluidAction.EXECUTE);

                long generated = drained.getAmount() * euPerLitre;
                euToGenerate -= generated;
                pendingPlasmaEU += generated;

                if (euToGenerate <= 0) return;
            }
        }
    }

    /** The EU the plasma in the input hatches is worth (it's only converted when a transfer needs it). */
    private long getPlasmaEUInHatches() {
        if (getLevel() == null || inputHatches.isEmpty()) return 0;

        List<GTRecipe> fuels = getLevel().getRecipeManager().getAllRecipesFor(GTRecipeTypes.PLASMA_GENERATOR_FUELS);

        long total = 0;

        for (FluidHatchPartMachine hatch : inputHatches) {
            for (int i = 0; i < hatch.tank.getTanks(); i++) {
                FluidStack fluid = hatch.tank.getFluidInTank(i);

                if (!fluid.isEmpty()) total += fluid.getAmount() * getEUPerLitre(fuels, fluid);
            }
        }

        return total;
    }

    private static long getEUPerLitre(List<GTRecipe> fuels, FluidStack fluid) {
        for (GTRecipe recipe : fuels) {
            for (FluidStack input : RecipeHelper.getInputFluids(recipe)) {
                if (input.getFluid() != fluid.getFluid() || input.getAmount() <= 0) continue;

                long eu = RecipeHelper.getRealEUt(recipe).getTotalEU() * recipe.duration;

                return eu / input.getAmount();
            }
        }

        return 0;
    }

    @Override
    public double drainPower(double requested) {
        // always keep 5 seconds worth of eu in the hatches to keep the uplink from powerfailing
        long toKeep = GTValues.V[GTValues.ZPM] * 20 * 5;

        long extractable = energyInput.getEnergyStored() - toKeep;

        if (extractable <= 0) return 0;

        long toDrain = Math.min(extractable, MMUtils.ceilLong(requested));

        return energyInput.removeEnergy(toDrain);
    }

    @Override
    public void submitPlan(Player submitter, String details, List<BigItemStack> requiredItems, boolean autocraft) {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();

        if (hatch != null) {
            String patternName = Component.translatable("mm.info.plan_name", submitter.getGameProfile().getName()).getString();

            if (details != null && !details.isEmpty()) {
                patternName += " (" + details + ")";
            }

            hatch.addRequest(submitter, patternName, requiredItems, autocraft);

            sendInfoToPlayer(submitter, "mm.info.new_virtual_me_pattern", patternName);
        } else {
            MMUtils.sendErrorToPlayer(submitter, uplinkHatches.isEmpty() ? "mm.uplink.status.no_hatch" : "mm.uplink.status.ae_offline");
        }
    }

    @Override
    public void clearManualPlans(Player player) {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();

        if (hatch != null) hatch.clearManualPlans(player);
    }

    @Override
    public void cancelAutoPlans(Player player) {
        MMUplinkMEHatchPartMachine hatch = getMEHatch();

        if (hatch != null) hatch.cancelAutoPlans(player);
    }

    // #endregion
}
