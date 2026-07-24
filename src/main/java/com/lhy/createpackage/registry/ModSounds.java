package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreatePackage.MODID);

    public static final RegistryObject<SoundEvent> MACHINE_LINKER_SUCCESS =
            register("machine_linker_success");
    public static final RegistryObject<SoundEvent> MACHINE_LINKER_ERROR =
            register("machine_linker_error");
    public static final RegistryObject<SoundEvent> MACHINE_LINKER_THUD =
            register("machine_linker_thud");

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(CreatePackage.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {}
}
