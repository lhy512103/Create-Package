package com.lhy.createpackage.registry;

import com.lhy.createpackage.CreatePackage;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, CreatePackage.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_LINKER_SUCCESS =
            register("machine_linker_success");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_LINKER_ERROR =
            register("machine_linker_error");
    public static final DeferredHolder<SoundEvent, SoundEvent> MACHINE_LINKER_THUD =
            register("machine_linker_thud");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreatePackage.MODID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    private ModSounds() {}
}
