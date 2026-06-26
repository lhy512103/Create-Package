package com.lhy.createpackage.content.converter;

import java.util.List;

import com.lhy.createpackage.CreatePackage;
import com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;
import com.lhy.createpackage.registry.ModMenuTypes;

import appeng.api.crafting.PatternDetailsHelper;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MechanicalPatternConverterMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    private static final int INTERNAL_SLOT_COUNT = 2;
    private static final int BUTTON_CONVERT = 0;

    private final InteractionHand hand;
    private final SimpleContainer container = new SimpleContainer(INTERNAL_SLOT_COUNT);

    public static MechanicalPatternConverterMenu fromNetwork(int windowId, Inventory inventory,
            RegistryFriendlyByteBuf buffer) {
        boolean mainHand = buffer.readBoolean();
        return new MechanicalPatternConverterMenu(ModMenuTypes.MECHANICAL_PATTERN_CONVERTER.get(), windowId,
                inventory, mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    public MechanicalPatternConverterMenu(MenuType<?> type, int windowId, Inventory inventory, InteractionHand hand) {
        super(type, windowId);
        this.hand = hand;

        addSlot(new InputSlot(container, INPUT_SLOT, 44, 35));
        addSlot(new OutputSlot(container, OUTPUT_SLOT, 116, 35));

        int invStartY = 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invStartY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, invStartY + 58));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).is(ModItems.MECHANICAL_PATTERN_CONVERTER.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CONVERT) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        convert(player);
        return true;
    }

    private void convert(Player player) {
        ItemStack input = container.getItem(INPUT_SLOT);
        if (input.isEmpty() || !isConvertiblePattern(input) || !container.getItem(OUTPUT_SLOT).isEmpty()) {
            return;
        }

        List<net.minecraft.core.BlockPos> route = routeFromBoundDistributor(player);
        if (route.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "gui." + CreatePackage.MODID + ".mechanical_pattern_converter.no_route"), true);
            return;
        }

        ItemStack originalPattern = input.copyWithCount(1);
        ItemStack result = new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get());
        result.set(ModComponents.MECHANICAL_PACKAGE_PATTERN.get(),
                new MechanicalPackagePatternData(originalPattern, route));

        input.shrink(1);
        if (input.isEmpty()) {
            container.setItem(INPUT_SLOT, ItemStack.EMPTY);
        }
        container.setItem(OUTPUT_SLOT, result);
        broadcastChanges();
    }

    private List<net.minecraft.core.BlockPos> routeFromBoundDistributor(Player player) {
        GlobalPos selected = player.getItemInHand(hand).get(ModComponents.LINKED_DISTRIBUTOR.get());
        if (selected == null || !selected.dimension().equals(player.level().dimension())) {
            return List.of();
        }
        if (!player.level().isLoaded(selected.pos())) {
            return List.of();
        }
        if (player.level().getBlockEntity(selected.pos()) instanceof PackageDistributorBlockEntity distributor) {
            return List.copyOf(distributor.getLinkedMachines());
        }
        return List.of();
    }

    private static boolean isConvertiblePattern(ItemStack stack) {
        return PatternDetailsHelper.isEncodedPattern(stack)
                && !stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index == OUTPUT_SLOT) {
            if (!moveItemStackTo(stack, INTERNAL_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index == INPUT_SLOT) {
            if (!moveItemStackTo(stack, INTERNAL_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isConvertiblePattern(stack)) {
            if (!moveItemStackTo(stack, INPUT_SLOT, INPUT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            clearContainer(player, container);
        }
    }

    private static final class InputSlot extends Slot {
        private InputSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isConvertiblePattern(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(SimpleContainer container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
