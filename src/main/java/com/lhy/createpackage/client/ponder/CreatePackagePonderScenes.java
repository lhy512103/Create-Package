package com.lhy.createpackage.client.ponder;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers Create Package ponder scenes. Storyboards are authored as Ponderer JSON DSL
 * under {@code assets/createpackage/ponderer/} and played by {@link PondererJsonScenes}.
 */
public final class CreatePackagePonderScenes {
    private CreatePackagePonderScenes() {
    }

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PondererJsonScenes.register(helper);
    }
}
