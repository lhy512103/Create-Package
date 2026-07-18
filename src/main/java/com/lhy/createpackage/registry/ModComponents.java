package com.lhy.createpackage.registry;

import java.util.function.Consumer;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Data component registry for Create Package.
 */
public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreatePackage.MODID);

    /**
     * The distributor a machine linker is currently bound to. Subsequent clicks on Create machines
     * link them to this distributor. Stored as a {@link GlobalPos} so it is dimension-safe.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINKED_DISTRIBUTOR =
            register("linked_distributor", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MechanicalPackagePatternData>> MECHANICAL_PACKAGE_PATTERN =
            register("mechanical_package_pattern", builder -> builder
                    .persistent(MechanicalPackagePatternData.CODEC)
                    .networkSynchronized(MechanicalPackagePatternData.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MachineRouteData>> MECHANICAL_ROUTE =
            register("mechanical_route", builder -> builder
                    .persistent(MachineRouteData.CODEC)
                    .networkSynchronized(MachineRouteData.STREAM_CODEC));

    /**
     * Currently selected route mark index for ordered route tools in Edit mode.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ROUTE_CURSOR =
            register("route_cursor", builder -> builder
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /**
     * Ordered-route tool mode. Absent/false = Append (default); true = Edit.
     * Toggled by left-clicking air while holding a supported route tool.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ROUTE_EDIT_MODE =
            register("route_edit_mode", builder -> builder
                    .persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));

    private static <T> DeferredHolder<DataComponentType<?>, DataComponentType<T>> register(
            String name, Consumer<DataComponentType.Builder<T>> customizer) {
        return DATA_COMPONENTS.register(name, () -> {
            var builder = DataComponentType.<T>builder();
            customizer.accept(builder);
            return builder.build();
        });
    }

    private ModComponents() {}
}
