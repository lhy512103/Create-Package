package com.lhy.createpackage.compat;

import java.util.ArrayList;
import java.util.Set;

import com.lhy.ae2utility.api.card.CardApi;
import com.lhy.ae2utility.api.card.NbtTearRule;
import com.lhy.ae2utility.card.NbtTearCardThreadLocal;
import com.lhy.ae2utility.card.NbtTearFilter;
import com.lhy.createpackage.content.CreatePackagePatternProviderMenu;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.AdvancedPackageDistributorMenu;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorBlockEntity;
import com.lhy.createpackage.content.distributor.BasicPackageDistributorMenu;
import com.lhy.createpackage.content.kinetic.ExtendedKineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.kinetic.ExtendedKineticPatternProviderMenu;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderBlockEntity;
import com.lhy.createpackage.content.kinetic.KineticPatternProviderMenu;
import com.lhy.createpackage.registry.ModBlocks;
import com.lhy.createpackage.registry.ModMenuTypes;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("createpackage")
@PrefixGameTestTemplate(false)
public final class Ae2UtilityGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    @GameTest(template = "empty")
    public static void basicDistributor(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.BASIC_PACKAGE_DISTRIBUTOR.get());
        BasicPackageDistributorBlockEntity host = helper.getBlockEntity(POS);
        var menu = new BasicPackageDistributorMenu(ModMenuTypes.BASIC_PACKAGE_DISTRIBUTOR.get(), 1,
                net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory(), host);
        verify(helper, host, menu);
    }

    @GameTest(template = "empty")
    public static void advancedDistributor(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.ADVANCED_PACKAGE_DISTRIBUTOR.get());
        AdvancedPackageDistributorBlockEntity host = helper.getBlockEntity(POS);
        var menu = new AdvancedPackageDistributorMenu(ModMenuTypes.ADVANCED_PACKAGE_DISTRIBUTOR.get(), 1,
                net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory(), host);
        verify(helper, host, menu);
    }

    @GameTest(template = "empty")
    public static void kineticProvider(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.KINETIC_PATTERN_PROVIDER.get());
        KineticPatternProviderBlockEntity host = helper.getBlockEntity(POS);
        var menu = new KineticPatternProviderMenu(ModMenuTypes.KINETIC_PATTERN_PROVIDER.get(), 1,
                net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory(), host);
        verify(helper, host, menu);
    }

    @GameTest(template = "empty")
    public static void extendedKineticProvider(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.EXTENDED_KINETIC_PATTERN_PROVIDER.get());
        ExtendedKineticPatternProviderBlockEntity host = helper.getBlockEntity(POS);
        var menu = new ExtendedKineticPatternProviderMenu(ModMenuTypes.EXTENDED_KINETIC_PATTERN_PROVIDER.get(), 1,
                net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory(), host);
        verify(helper, host, menu);
    }

    private static void verify(GameTestHelper helper, PatternProviderLogicHost host,
            CreatePackagePatternProviderMenu menu) {
        var logic = host.getLogic();
        var hostUpgrades = ((appeng.api.upgrades.IUpgradeableObject) host).getUpgrades();
        helper.assertTrue(hostUpgrades.size() == 4, "Every built-in provider has four host upgrade slots");
        long visibleHostSlots = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).stream()
                .filter(candidate -> candidate instanceof appeng.menu.slot.AppEngSlot appSlot
                        && appSlot.isSlotEnabled() && appSlot.getInventory() == hostUpgrades).count();
        helper.assertTrue(visibleHostSlots == 4, "The dynamic upgrade background receives all four visible slots");
        if (host instanceof AdvancedPackageDistributorBlockEntity || host instanceof KineticPatternProviderBlockEntity) {
            ItemStack remainingCards = hostUpgrades.addItems(com.lhy.createpackage.registry.ModItems.PARALLEL_CARD.toStack(4));
            helper.assertTrue(remainingCards.isEmpty(), "All four parallel cards can be installed");
            helper.assertTrue(hostUpgrades.addItems(com.lhy.createpackage.registry.ModItems.PARALLEL_CARD.toStack()).getCount() == 1,
                    "A fifth parallel card cannot be inserted");
            int capacity = host instanceof AdvancedPackageDistributorBlockEntity distributor
                    ? distributor.getMaxActiveJobs() : ((KineticPatternProviderBlockEntity) host).getMaxParallelMachines();
            helper.assertTrue(capacity == 64, "Four parallel cards provide 64 concurrent jobs by default");
            CompoundTag upgradeSave = new CompoundTag();
            hostUpgrades.writeToNBT(upgradeSave, "testUpgrades", helper.getLevel().registryAccess());
            hostUpgrades.clear();
            hostUpgrades.readFromNBT(upgradeSave, "testUpgrades", helper.getLevel().registryAccess());
            helper.assertTrue(hostUpgrades.getInstalledUpgrades(com.lhy.createpackage.registry.ModItems.PARALLEL_CARD.get()) == 4,
                    "All four installed cards survive save/load");
        }
        var sharedSlots = menu.getSlots(appeng.menu.SlotSemantics.UPGRADE).stream()
                .filter(candidate -> candidate instanceof appeng.menu.slot.AppEngSlot appSlot
                        && appSlot.isSlotEnabled() && appSlot.getInventory() == hostUpgrades).toList();
        helper.assertTrue(menu.slots.stream().noneMatch(candidate -> candidate.getClass().getName()
                .equals("com.lhy.ae2utility.menu.PatternProviderTearSlot")), "No fifth dedicated slot is added");
        var slot = sharedSlots.get(3);
        hostUpgrades.setItemDirect(3, ItemStack.EMPTY);
        ItemStack card = com.lhy.ae2utility.init.ModItems.NBT_TEAR_CARD.toStack();
        CardApi.INSTANCE.setNbtTearRule(card, new NbtTearRule(Set.of(ResourceLocation.parse("minecraft:diamond"))));
        helper.assertTrue(slot.mayPlace(card), "Card can be inserted");
        helper.assertTrue(!slot.mayPlace(Items.STONE.getDefaultInstance()), "Ordinary items cannot enter upgrade slots");
        helper.assertTrue(slot.getMaxStackSize(card) == 1, "Only one card fits");
        menu.getPlayerInventory().setItem(0, card.copy());
        var playerSlot = menu.slots.stream()
                .filter(candidate -> candidate.container == menu.getPlayerInventory() && candidate.getContainerSlot() == 0)
                .findFirst().orElseThrow();
        menu.quickMoveStack(menu.getPlayer(), playerSlot.index);
        helper.assertTrue(Ae2UtilityCompat.isTearCard(Ae2UtilityCompat.findInstalledCard(hostUpgrades)), "Shift-click inserts into the effective inventory");
        helper.assertTrue(menu.getPlayerInventory().getItem(0).isEmpty(), "Shift-click moves rather than duplicates the card");

        helper.assertTrue(hostUpgrades.addItems(card.copy()).getCount() == 1, "A second tear card is rejected");
        if (host instanceof AdvancedPackageDistributorBlockEntity || host instanceof KineticPatternProviderBlockEntity) {
            int capacity = host instanceof AdvancedPackageDistributorBlockEntity distributor
                    ? distributor.getMaxActiveJobs() : ((KineticPatternProviderBlockEntity) host).getMaxParallelMachines();
            helper.assertTrue(capacity == 48, "Three parallel cards plus one tear card share four slots and provide 48 jobs");
        }
        var diamond = AEItemKey.of(Items.DIAMOND);
        var namedDiamond = AEItemKey.of(named(Items.DIAMOND.getDefaultInstance()));
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        var namedGold = AEItemKey.of(named(Items.GOLD_INGOT.getDefaultInstance()));
        helper.assertTrue(Ae2UtilityCompat.matches(logic, diamond, namedDiamond), "Whitelisted data mismatch matches");
        helper.assertTrue(!Ae2UtilityCompat.matches(logic, gold, namedGold), "Non-whitelisted data mismatch stays strict");
        helper.assertTrue(!Ae2UtilityCompat.matches(logic, diamond, gold), "Different items never match");
        helper.assertTrue(Ae2UtilityCompat.matches(logic, gold, gold), "Exact matches remain valid");

        var encoded = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(new appeng.api.stacks.GenericStack(diamond, 1)),
                java.util.List.of(new appeng.api.stacks.GenericStack(gold, 1)));
        var pattern = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(encoded));
        var actualInputs = new appeng.api.stacks.KeyCounter();
        actualInputs.add(namedDiamond, 1);
        var inputHolders = new appeng.api.stacks.KeyCounter[] {actualInputs};
        helper.assertTrue(Ae2UtilityCompat.acceptsProcessingInputs(card, pattern, inputHolders),
                "This provider's card accepts actual input components");
        helper.assertTrue(!Ae2UtilityCompat.acceptsProcessingInputs(ItemStack.EMPTY, pattern, inputHolders),
                "A different provider without a card cannot consume fuzzy inputs");

        ItemStack unrestricted = com.lhy.ae2utility.init.ModItems.NBT_TEAR_CARD.toStack();
        helper.assertTrue(Ae2UtilityCompat.matches(unrestricted, gold, namedGold), "Empty whitelist allows all ordinary items");
        var injector = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.parse("draconicevolution:basic_crafting_injector"));
        helper.assertTrue(injector != Items.AIR, "Runtime supplies a blacklisted injector for verification");
        helper.assertTrue(!Ae2UtilityCompat.matches(unrestricted, AEItemKey.of(injector),
                AEItemKey.of(named(injector.getDefaultInstance()))), "Utility's blacklist overrides unrestricted matching");

        CompoundTag saved = new CompoundTag();
        hostUpgrades.writeToNBT(saved, "upgrades", helper.getLevel().registryAccess());
        for (int i = 0; i < hostUpgrades.size(); i++) {
            if (Ae2UtilityCompat.isTearCard(hostUpgrades.getStackInSlot(i))) hostUpgrades.setItemDirect(i, ItemStack.EMPTY);
        }
        helper.assertTrue(!Ae2UtilityCompat.matches(logic, diamond, namedDiamond), "Removing the card restores strict matching");
        hostUpgrades.readFromNBT(saved, "upgrades", helper.getLevel().registryAccess());
        helper.assertTrue(Ae2UtilityCompat.matches(logic, diamond, namedDiamond), "Saved card and whitelist survive reload");

        var outer = new NbtTearFilter(java.util.List.of(ResourceLocation.parse("minecraft:gold_ingot")));
        NbtTearCardThreadLocal.set(outer);
        try {
            Ae2UtilityCompat.withTearCard(card, () -> {
                ItemStack captured = Ae2UtilityCompat.captureTearCard(null);
                helper.assertTrue(Ae2UtilityCompat.matches(captured, diamond, namedDiamond),
                        "External provider rule reaches an ordinary distributor");
                helper.assertTrue(!Ae2UtilityCompat.matches(captured, gold, namedGold), "Outer provider rule cannot leak");
                throw new IllegalStateException("test-scope-cleanup");
            });
        } catch (IllegalStateException expected) {
            helper.assertTrue("test-scope-cleanup".equals(expected.getMessage()), "Expected scope test exception");
        }
        helper.assertTrue(NbtTearCardThreadLocal.get() == outer, "Scope is restored even after an exception");
        NbtTearCardThreadLocal.clear();

        var drops = new ArrayList<ItemStack>();
        ((appeng.blockentity.AEBaseBlockEntity) host.getBlockEntity()).addAdditionalDrops(
                helper.getLevel(), host.getBlockEntity().getBlockPos(), drops);
        helper.assertTrue(drops.stream().filter(Ae2UtilityCompat::isTearCard).mapToInt(ItemStack::getCount).sum() == 1,
                "Breaking the provider drops exactly one card");
        if (host instanceof com.lhy.createpackage.content.distributor.PackageDistributorBlockEntity distributor) {
            distributor.clearContent();
        } else {
            ((KineticPatternProviderBlockEntity) host).clearContent();
        }
        helper.assertTrue(Ae2UtilityCompat.findInstalledCard(hostUpgrades).isEmpty(), "Clearing the provider removes the card");
        var legacy = (net.neoforged.neoforge.items.IItemHandlerModifiable) Ae2UtilityCompat.tearInventory(logic);
        legacy.setStackInSlot(0, card.copy());
        Ae2UtilityCompat.migrateLegacyCard(host, hostUpgrades);
        helper.assertTrue(legacy.getStackInSlot(0).isEmpty()
                && Ae2UtilityCompat.isTearCard(Ae2UtilityCompat.findInstalledCard(hostUpgrades)),
                "Legacy dedicated card migrates into a shared slot");
        hostUpgrades.clear();
        if (host instanceof AdvancedPackageDistributorBlockEntity || host instanceof KineticPatternProviderBlockEntity) {
            hostUpgrades.addItems(com.lhy.createpackage.registry.ModItems.PARALLEL_CARD.toStack(4));
            legacy.setStackInSlot(0, card.copy());
            Ae2UtilityCompat.migrateLegacyCard(host, hostUpgrades);
            helper.assertTrue(legacy.getStackInSlot(0).isEmpty(), "Full-inventory migration clears the retired slot");
            var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(host.getBlockEntity().getBlockPos()).inflate(1));
            helper.assertTrue(dropped.stream().filter(entity -> Ae2UtilityCompat.isTearCard(entity.getItem()))
                    .mapToInt(entity -> entity.getItem().getCount()).sum() == 1,
                    "If all four slots are full the legacy card drops once, without loss or duplication");
        }
        helper.succeed();
    }

    private static ItemStack named(ItemStack stack) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("NBT tear test"));
        return stack;
    }
}
