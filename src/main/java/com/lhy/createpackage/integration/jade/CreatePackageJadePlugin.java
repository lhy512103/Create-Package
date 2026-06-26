package com.lhy.createpackage.integration.jade;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlock;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(CreatePackage.MODID)
public class CreatePackageJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PackageDistributorComponentProvider.INSTANCE, PackageDistributorBlock.class);
    }
}
