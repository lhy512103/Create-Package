package com.lhy.createpackage.content.distributor;


import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

public class PackageDistributorBlockItem extends BlockItem {
    public PackageDistributorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (SequencedAssemblyData.has(context.getItemInHand())) {
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }
}
