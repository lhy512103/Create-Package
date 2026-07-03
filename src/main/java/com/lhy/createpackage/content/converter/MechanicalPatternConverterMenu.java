package com.lhy.createpackage.content.converter;

import java.util.List;

import com.lhy.createpackage.content.pattern.MachineRouteData;
import com.lhy.createpackage.content.pattern.MechanicalPackagePatternData;
import com.lhy.createpackage.registry.ModComponents;
import com.lhy.createpackage.registry.ModItems;
import com.lhy.createpackage.registry.ModMenuTypes;

import appeng.api.crafting.PatternDetailsHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class MechanicalPatternConverterMenu extends AbstractContainerMenu {
    public static final int INPUT_SLOT_START = 0;
    public static final int INPUT_SLOT_COUNT = 9;
    public static final int OUTPUT_SLOT_START = INPUT_SLOT_START + INPUT_SLOT_COUNT;
    public static final int OUTPUT_SLOT_COUNT = 9;
    public static final int INTERNAL_SLOT_COUNT = INPUT_SLOT_COUNT + OUTPUT_SLOT_COUNT;
    public static final int BUTTON_CONVERT = 0;

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

        for (int col = 0; col < INPUT_SLOT_COUNT; col++) {
            addSlot(new InputSlot(container, INPUT_SLOT_START + col, 8 + col * 18, 45));
        }
        for (int col = 0; col < OUTPUT_SLOT_COUNT; col++) {
            addSlot(new OutputSlot(container, OUTPUT_SLOT_START + col, 8 + col * 18, 97));
        }

        int invStartY = 131;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invStartY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 189));
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
        if (!hasConvertibleSlot()) {
            return;
        }

        List<net.minecraft.core.BlockPos> route = routeFromConverter(player);

        boolean converted = false;
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            int inputSlot = INPUT_SLOT_START + i;
            int outputSlot = OUTPUT_SLOT_START + i;
            ItemStack input = container.getItem(inputSlot);
            if (input.isEmpty() || !isConvertiblePattern(input) || !container.getItem(outputSlot).isEmpty()) {
                continue;
            }

            ItemStack originalPattern = originalPattern(input);
            if (originalPattern.isEmpty()) {
                continue;
            }
            ItemStack result = new ItemStack(ModItems.MECHANICAL_PACKAGE_PATTERN.get());
            result.set(ModComponents.MECHANICAL_PACKAGE_PATTERN.get(),
                    new MechanicalPackagePatternData(originalPattern, route,
                            java.util.Optional.of(MechanicalPackagePatternData.PatternEncoder.of(player))));

            input.shrink(1);
            if (input.isEmpty()) {
                container.setItem(inputSlot, ItemStack.EMPTY);
            }
            container.setItem(outputSlot, result);
            converted = true;
        }
        if (converted) {
            broadcastChanges();
        }
    }

    private boolean hasConvertibleSlot() {
        for (int i = 0; i < INPUT_SLOT_COUNT; i++) {
            ItemStack input = container.getItem(INPUT_SLOT_START + i);
            if (!input.isEmpty()
                    && isConvertiblePattern(input)
                    && container.getItem(OUTPUT_SLOT_START + i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<net.minecraft.core.BlockPos> routeFromConverter(Player player) {
        MachineRouteData route = player.getItemInHand(hand).get(ModComponents.MECHANICAL_ROUTE.get());
        return route == null ? List.of() : route.positions();
    }

    private static boolean isConvertiblePattern(ItemStack stack) {
        if (stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
            return data != null && PatternDetailsHelper.isEncodedPattern(data.encodedPattern());
        }
        return PatternDetailsHelper.isEncodedPattern(stack);
    }

    private static ItemStack originalPattern(ItemStack stack) {
        if (stack.is(ModItems.MECHANICAL_PACKAGE_PATTERN.get())) {
            MechanicalPackagePatternData data = MechanicalPackagePatternData.from(stack);
            return data == null ? ItemStack.EMPTY : data.encodedPattern().copyWithCount(1);
        }
        return stack.copyWithCount(1);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (isOutputSlot(index)) {
            if (!moveItemStackTo(stack, INTERNAL_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isInputSlot(index)) {
            if (!moveItemStackTo(stack, INTERNAL_SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isConvertiblePattern(stack)) {
            if (!moveItemStackTo(stack, INPUT_SLOT_START, OUTPUT_SLOT_START, false)) {
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

    private static boolean isInputSlot(int index) {
        return index >= INPUT_SLOT_START && index < OUTPUT_SLOT_START;
    }

    private static boolean isOutputSlot(int index) {
        return index >= OUTPUT_SLOT_START && index < INTERNAL_SLOT_COUNT;
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
            return 1;
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
