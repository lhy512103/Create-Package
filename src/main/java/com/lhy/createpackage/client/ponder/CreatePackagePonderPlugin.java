package com.lhy.createpackage.client.ponder;

import com.lhy.createpackage.CreatePackage;

import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CreatePackagePonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return CreatePackage.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        CreatePackagePonderScenes.register(helper);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        CreatePackagePonderTags.register(helper);
    }
}
