package com.raishxn.modern_manipulator.common.compat.gt;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.IBlockApplyContext;
import com.raishxn.modern_manipulator.common.building.ITileAnalysisIntegration;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.building.SmartCopyIntegration;
import com.raishxn.modern_manipulator.common.building.SmartCopyIntegration.SmartCopyAction;
import com.raishxn.modern_manipulator.common.compat.SmartCopyHandlers;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import java.util.List;

/**
 * Auto-Proxy CRIBs: ME pattern buffers in the copied region are pasted as pattern buffer proxies linked to the source
 * buffer. Only loaded when GregTech and AE2 are present.
 */
public class GTSmartCopy {

    private GTSmartCopy() {}

    public static void init() {
        SmartCopyHandlers.register((state, world, blocks, coordA) -> {
            if (!state.config.replaceCribsWithProxies || !state.hasCap(ItemMatterManipulator.ALLOW_SMART_COPY)) return;

            BlockState proxyDefault = GTAEMachines.ME_PATTERN_BUFFER_PROXY.getBlock().defaultBlockState();

            for (PendingBlock block : blocks) {
                BlockPos src = new BlockPos(coordA.x + block.x, coordA.y + block.y, coordA.z + block.z);

                if (!(world.getBlockEntity(src) instanceof IMachineBlockEntity mbe)) continue;
                if (!(mbe.getMetaMachine() instanceof MEPatternBufferPartMachine)) continue;

                BlockState proxy = copyProperties(block.getBlockState(), proxyDefault);

                block.spec = new BlockSpec().setObject(proxy);

                SmartCopyIntegration sc = new SmartCopyIntegration();
                sc.action = SmartCopyAction.CRIB_TO_PROXY;
                sc.sourceX = src.getX();
                sc.sourceY = src.getY();
                sc.sourceZ = src.getZ();
                block.smartCopy = sc;

                block.gt = new ProxyLink(src);
                block.ae = null;
                block.inventory = null;
            }
        });
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static BlockState copyProperties(BlockState from, BlockState to) {
        for (Property property : from.getProperties()) {
            if (to.hasProperty(property)) to = to.setValue(property, from.getValue(property));
        }

        return to;
    }

    /**
     * Links a placed pattern buffer proxy to its source buffer.
     */
    public static class ProxyLink implements ITileAnalysisIntegration {

        public final BlockPos source;

        public ProxyLink(BlockPos source) {
            this.source = source;
        }

        @Override
        public boolean apply(IBlockApplyContext ctx) {
            if (ctx.getTileEntity() instanceof IMachineBlockEntity mbe &&
                    mbe.getMetaMachine() instanceof MEPatternBufferProxyPartMachine proxy) {
                if (proxy.getBuffer() == null || !source.equals(proxy.getBuffer().getPos())) {
                    proxy.setBuffer(source);
                }
            } else {
                ctx.warn(Component.translatable("mm.info.warning.could_not_link_proxy"));
            }

            return true;
        }

        @Override
        public boolean getRequiredItemsForExistingBlock(IBlockApplyContext context) {
            return true;
        }

        @Override
        public boolean getRequiredItemsForNewBlock(IBlockApplyContext context) {
            return true;
        }

        @Override
        public void getItemTag(ItemStack stack) {}

        @Override
        public void getItemDetailsChat(List<Component> details) {}

        @Override
        public void transform(Transform transform) {
            // the source position is absolute
        }

        @Override
        public ProxyLink clone() {
            return new ProxyLink(source);
        }

        @Override
        public void migrate() {}
    }
}
