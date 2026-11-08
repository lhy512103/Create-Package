package com.lhy.createpackage.content.crafting;

import java.util.List;

import com.lhy.createpackage.CreatePackage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** Real Vintage recipes and machines, without a compile-time dependency on the optional addon. */
@GameTestHolder("createpackage")
@PrefixGameTestTemplate(false)
public final class VintagePressurizingGameTests {
    private static final BlockPos BASIN = new BlockPos(0, 0, 0);
    private static final BlockPos CHAMBER = new BlockPos(0, 2, 0);
    private static final BlockPos PROVIDER = new BlockPos(3, 1, 3);

    @GameTest(template = "empty")
    public static void sulfuricAcidRoutesPrimaryAndSecondaryInputs(GameTestHelper helper) {
        prepare(helper, "sulfuric_acid", () -> {
            Fluid trioxide = fluid("vintage:sulfur_trioxide");
            Fluid water = fluid("minecraft:water");
            var plan = plan(helper, "vintage:sulfuric_acid", 100,
                    List.of(stack(trioxide, 500), stack(water, 500)));
            helper.assertTrue(plan.isSuccess(), "Real sulfuric acid recipe is recognized: " + plan.statusKey());
            helper.assertTrue(plan.outputPos().equals(helper.absolutePos(BASIN)), "Acid is collected from the basin");
            helper.assertTrue(plan.outputEndpoints().equals(List.of(MachineEndpoint.smartFluidOutput(helper.absolutePos(BASIN)))),
                    "Only basin output is exposed; chamber water input is never an output endpoint");
            helper.assertTrue(plan.simulate(), "Both 500 mB inputs fit their recipe-selected tanks");
            assertEmpty(helper);
            helper.assertTrue(plan.execute().success(), "Sulfuric acid input dispatch succeeds");
            IFluidHandler basin = InventoryAccess.fluidHandler(helper.getLevel(), helper.absolutePos(BASIN));
            IFluidHandler chamberInput = InventoryAccess.fluidInputHandler(helper.getLevel(), helper.absolutePos(CHAMBER));
            helper.assertTrue(amount(basin, trioxide) == 500 && total(basin) == 500,
                    "Basin contains exactly 500 mB sulfur trioxide, not the secondary water");
            helper.assertTrue(amount(chamberInput, water) == 500 && total(chamberInput) == 500,
                    "Chamber input contains exactly 500 mB water, not sulfur trioxide");
            helper.assertTrue(total(InventoryAccess.fluidOutputHandler(helper.getLevel(), helper.absolutePos(CHAMBER))) == 0,
                    "Input dispatch does not populate the chamber output tank");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sulfuricAcidRejectsMissingWaterWithoutInsertion(GameTestHelper helper) {
        prepare(helper, "sulfuric_acid", () -> {
            var plan = plan(helper, "vintage:sulfuric_acid", 100,
                    List.of(stack(fluid("vintage:sulfur_trioxide"), 500)));
            helper.assertTrue(!plan.isSuccess(), "Missing secondary water rejects the entire pattern");
            helper.assertTrue(!plan.simulate(), "Incomplete pattern cannot simulate successfully");
            assertEmpty(helper);
            helper.assertTrue(!plan.execute().success(), "Rejected pattern cannot execute partial insertion");
            assertEmpty(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sulfurDioxideUsesOnlyChamberOutput(GameTestHelper helper) {
        prepare(helper, "sulfur_dioxide", () -> {
            var sulfurId = new ResourceLocation("vintage", "sulfur");
            ItemStack sulfur = BuiltInRegistries.ITEM.getOptional(sulfurId).orElseThrow().getDefaultInstance();
            helper.assertTrue(sulfur.is(TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    new ResourceLocation("forge", "gems/sulfur"))), "Registered Vintage sulfur matches the real recipe tag");
            var plan = plan(helper, "vintage:sulfur_dioxide", 1000,
                    List.of(new GenericStack(AEItemKey.of(sulfur), 1)));
            helper.assertTrue(plan.isSuccess(), "Real sulfur dioxide recipe is recognized: " + plan.statusKey());
            helper.assertTrue(plan.outputPos().equals(helper.absolutePos(CHAMBER)),
                    "secondaryFluidOutput=0 selects the chamber as primary collection position");
            helper.assertTrue(plan.outputEndpoints().equals(List.of(MachineEndpoint.smartFluidOutput(helper.absolutePos(CHAMBER)))),
                    "Only chamber output is exposed, never basin fluid or chamber input");
            helper.assertTrue(plan.simulate(), "Sulfur gem fits the basin");
            assertEmpty(helper);
            helper.assertTrue(plan.execute().success(), "Sulfur input dispatch succeeds");
            IItemHandler basinItems = InventoryAccess.itemHandler(helper.getLevel(), helper.absolutePos(BASIN));
            helper.assertTrue(itemCount(basinItems) == 1, "Exactly one sulfur gem is inserted into the basin");
            helper.assertTrue(basinItems != null && java.util.stream.IntStream.range(0, basinItems.getSlots())
                    .anyMatch(slot -> basinItems.getStackInSlot(slot).is(sulfur.getItem())), "Inserted gem is Vintage sulfur");
            helper.assertTrue(total(InventoryAccess.fluidHandler(helper.getLevel(), helper.absolutePos(BASIN))) == 0,
                    "Item-only dispatch inserts no fluid into the basin");
            helper.assertTrue(total(InventoryAccess.fluidInputHandler(helper.getLevel(), helper.absolutePos(CHAMBER))) == 0,
                    "Sulfur dioxide recipe requires no secondary fluid input");
            helper.succeed();
        });
    }

    private static void prepare(GameTestHelper helper, String recipe, Runnable assertions) {
        if (!ModList.get().isLoaded("vintage")) {
            // Forge 1.20.1 has no native skip result: make the conditional success explicit in the log.
            CreatePackage.LOGGER.warn("[Vintage pressurizing GameTest] SKIPPED {}: optional mod 'vintage' is not loaded", recipe);
            helper.succeed();
            return;
        }
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(
                new ResourceLocation("vintage", "pressurizing/" + recipe)).isPresent(), "Real Vintage recipe must be loaded: " + recipe);
        helper.setBlock(BASIN, BuiltInRegistries.BLOCK.getOptional(new ResourceLocation("create", "basin")).orElseThrow());
        helper.setBlock(CHAMBER, BuiltInRegistries.BLOCK.getOptional(new ResourceLocation("vintage", "vacuum_chamber")).orElseThrow());
        // Let Create initialize the SmartFluidTankBehaviour capabilities before querying them.
        helper.runAfterDelay(1, () -> {
            var chamber = helper.getBlockEntity(CHAMBER);
            helper.assertTrue(chamber != null, "Vacuum chamber block entity exists");
            CompoundTag tag = chamber.saveWithoutMetadata();
            tag.putBoolean("Mode", true); // Real Vintage NBT: true = pressurizing, false = vacuumizing.
            chamber.load(tag);
            // No kinetic power is supplied: this test checks dispatch, not 600-tick processing or burner heat.
            assertEmpty(helper);
            assertions.run();
        });
    }

    private static MachinePlanFactory.Plan plan(GameTestHelper helper, String output, int amount, List<GenericStack> inputs) {
        AEFluidKey key = AEFluidKey.of(fluid(output));
        return MachinePlanFactory.create(helper.getLevel(), helper.absolutePos(CHAMBER), helper.absolutePos(PROVIDER),
                key, amount, List.of(new GenericStack(key, amount)), inputs);
    }

    private static Fluid fluid(String id) {
        return BuiltInRegistries.FLUID.getOptional(new ResourceLocation(id)).orElseThrow();
    }

    private static GenericStack stack(Fluid fluid, int amount) {
        // AE2 15 represents fluid amounts in mB (AMOUNT_BUCKET = 1000).
        return new GenericStack(AEFluidKey.of(fluid), amount);
    }

    private static void assertEmpty(GameTestHelper helper) {
        helper.assertTrue(itemCount(InventoryAccess.itemHandler(helper.getLevel(), helper.absolutePos(BASIN))) == 0,
                "Planning/simulation leaves basin items untouched");
        helper.assertTrue(total(InventoryAccess.fluidHandler(helper.getLevel(), helper.absolutePos(BASIN))) == 0,
                "Planning/simulation leaves basin fluids untouched");
        helper.assertTrue(total(InventoryAccess.fluidInputHandler(helper.getLevel(), helper.absolutePos(CHAMBER))) == 0,
                "Planning/simulation leaves chamber input untouched");
        helper.assertTrue(total(InventoryAccess.fluidOutputHandler(helper.getLevel(), helper.absolutePos(CHAMBER))) == 0,
                "Planning/simulation leaves chamber output untouched");
    }

    private static int itemCount(IItemHandler handler) {
        if (handler == null) {
            throw new IllegalStateException("Missing real basin item capability");
        }
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            count += handler.getStackInSlot(slot).getCount();
        }
        return count;
    }

    private static int total(IFluidHandler handler) {
        if (handler == null) {
            throw new IllegalStateException("Missing real basin/chamber fluid capability");
        }
        int total = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            total += handler.getFluidInTank(tank).getAmount();
        }
        return total;
    }

    private static int amount(IFluidHandler handler, Fluid fluid) {
        if (handler == null) {
            throw new IllegalStateException("Missing real fluid capability");
        }
        int amount = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            var stack = handler.getFluidInTank(tank);
            if (stack.getFluid() == fluid) {
                amount += stack.getAmount();
            }
        }
        return amount;
    }
}
