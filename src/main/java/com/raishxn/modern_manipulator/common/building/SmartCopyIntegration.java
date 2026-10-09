package com.raishxn.modern_manipulator.common.building;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import com.raishxn.modern_manipulator.common.items.manipulator.Transform;

import java.util.ArrayList;
import java.util.List;

public class SmartCopyIntegration implements ITileAnalysisIntegration {

    public enum SmartCopyAction {
        NONE,
        CRIB_TO_PROXY,
        INTERFACE_TO_P2P
    }

    public static class P2PInfo {

        public short freq;
        public int srcX, srcY, srcZ;
        public Direction srcSide;
        public Direction destSide;
        public PortableItemStack p2pItem;

        @Override
        @SuppressWarnings("MethodDoesntCallSuperMethod")
        public P2PInfo clone() {
            P2PInfo dup = new P2PInfo();
            dup.freq = freq;
            dup.srcX = srcX;
            dup.srcY = srcY;
            dup.srcZ = srcZ;
            dup.srcSide = srcSide;
            dup.destSide = destSide;
            dup.p2pItem = p2pItem;
            return dup;
        }
    }

    public SmartCopyAction action = SmartCopyAction.NONE;
    public int sourceX, sourceY, sourceZ;
    public List<P2PInfo> p2pActions;

    @Override
    public boolean apply(IBlockApplyContext ctx) {
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
        if (p2pActions != null) {
            for (P2PInfo info : p2pActions) {
                info.destSide = transform.apply(info.destSide);
            }
        }
    }

    @Override
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    public SmartCopyIntegration clone() {
        SmartCopyIntegration dup = new SmartCopyIntegration();
        dup.action = action;
        dup.sourceX = sourceX;
        dup.sourceY = sourceY;
        dup.sourceZ = sourceZ;

        if (p2pActions != null) {
            dup.p2pActions = new ArrayList<>();
            for (P2PInfo info : p2pActions) dup.p2pActions.add(info.clone());
        }

        return dup;
    }

    @Override
    public void migrate() {}
}
