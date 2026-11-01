---
navigation:
  parent: createpackage:create-package.md
  title: Kinetic Pattern Provider
  icon: kinetic_pattern_provider
  position: 256
categories:
- devices
item_ids:
- createpackage:kinetic_pattern_provider
---

# Kinetic Pattern Provider

<ItemImage id="kinetic_pattern_provider" scale="6" />

AE2 pattern provider for one Create machine, or many matching machines after parallel upgrades. Use it when a full sequenced-assembly route is unnecessary.

Place it on the ME network, wrench-set the target side, and insert normal AE2 <ItemLink id="ae2:processing_pattern" />s. By default only the front machine is used.

## Parallel machines

Install <ItemLink id="parallel_card" />s, select the provider with a <ItemLink id="machine_linker" />, then link machines of the same type as the front target.

* 0 cards: front machine only
* 1 card: up to 16 active machines
* 2 cards: up to 32 active machines
* 3 cards: up to 48 active machines
* 4 cards: up to 64 active machines

New jobs go to idle linked machines first.

## Special machine targets and outputs

### Crushing Wheels

Face the automatically created `create:crushing_wheel_controller` in the gap, not either wheel block. The wheels must share an axis, have one block between them, and turn in opposite directions. For downward processing, put the recovery Depot or Belt directly below the **middle processing block**.

The controller is invisible and has no normal black selection outline. A temporary support helps place the provider beside the gap before wrench-setting its target side. To link another pair, put a visible reference block behind its gap and aim through the gap at that reference. Confirm the controller ID using F3, then right-click with the linker already bound to the provider. Remove the reference afterward. Clicking a wheel does not redirect to its controller.

### Mechanical Crafters

Place the provider against one crafter and set a single target face toward it. Build enough same-facing, four-neighbor-connected crafters in one plane for the actual recipe. Drive the integrated gears through an adjacent cogwheel; a motor directly against a crafter back is not a shaft connection.

Use an **AE processing pattern**, with the exact ingredients and yield for one recipe, rather than an AE crafting pattern. The adapter recovers the large layout from the recipe and, after planning succeeds, arranges arrows, joins the necessary input groups and supplies the cells. No G scan or manual arrow setup is required. Remove covers from ingredient cells and clear old contents.

Products need an exit into an inventory directly adjacent within the crafter wall plane. A provider at an in-plane edge can receive them directly. A provider against the wall front or back needs a separate in-plane chest or Depot. The output inventory need not touch the provider.

For parallel work, link one representative per independent physical group; the front group is already targeted. Do not batch-link every cell of one wall as separate production lines. Links currently count block coordinates, while running work claims the actual chain. Separate groups with air and give each its own output.

### Saws and working positions

A Saw must face up and rotate. Its exit is an adjacent block at the same height, determined by its axis and rotation. Reversing rotation changes that exit, so move or add the recovery carrier accordingly. The provider sets the Saw filter from the pattern primary output.

A Mixer requires a Basin two blocks below it for item/fluid inputs and output recovery, plus the real recipe speed and heat. A Press uses a Basin two blocks below for basin recipes or a Depot/Belt there for single-item pressing. A Spout also uses a working position two blocks below, but fluid goes directly into its own tank and it needs no rotation. Target the processing machine itself in each case.

## FTB Ultimine batch operations

Installing FTB Ultimine enables the Machine Linker integration automatically. Keep the <ItemLink id="machine_linker" /> in hand throughout.

1. Release the Ultimine and sneak keys, then right-click the provider to select it. Set a valid front machine first.
2. Hold the configured Ultimine key (grave accent by default). Normally hold Sneak as well and scroll to select a shape; if Ultimine itself is bound to Shift, its shape menu uses Ctrl. Check the preview.
3. **Release Sneak but keep Ultimine held**, then right-click a selected machine to batch-link matching targets.
4. **Keep Sneak held** with Ultimine and right-click a machine to batch-unlink. This removes links, not blocks.
5. Release Ultimine before sneak-right-clicking the provider to clear all extra links and the linker selection.

Only loaded positions with the clicked block ID and the front machine type are accepted. FTB settings limit the selection size; process several selections if needed. Links can be saved without a Parallel Card, but only the front target is active then. Check key conflicts, hunger, shape and installation if no preview or action appears. For Mechanical Crafters, select one representative per independent group rather than an entire wall of duplicate candidates.

## Supported targets

* **Deployer**: processed item to the working position, held item into the deployer. A non-consumed held item already placed in the deployer may be omitted from the pattern.
* **Spout**: item to the working position, fluid into the spout
* **Mechanical Press**: depot/belt or basin mode
* **Mechanical Mixer**: basin recipes (items + fluids)
* **Mechanical Saw**: sets filter from the primary item output; recovers from the fixed output side
* **Millstone**: recovers directly from the Millstone inventory without an external output Depot
* **Crushing Wheels**: targets the middle controller and recovers from its actual output Depot or Belt
* **Mechanical Crafters**: plans connected crafters from actual recipes and uses a valid in-plane product exit
* **Create Enchantment Industry**: Mechanical Grindstone over a Grindstone Drain. Face either block; item outputs are recovered from the drain's ejection side (belt/depot or dropped items). The wheel and drain must spin in opposite directions.
* **CreateMoreMachines**: same roles by registry-id suffix, using standard item/fluid capabilities
* **Create Crafts & Additions**: Rolling Mill uses its internal inventory; an upward-facing Tesla Coil works over a Depot or Belt two blocks below it
* **Create Diesel Generators**: Basin Lid feeds and collects from the Basin directly below it; Bulk Fermenter accepts mixed item/fluid pattern inputs through its multiblock capabilities
* **Vintage Improvements Compressor**: requires a Basin two blocks below. For `pressurizing` recipes, every fluid is sent according to the recipe's secondary-fluid marker: Basin fluids stay in the Basin, compressor fluids go into the Vacuum Chamber, and fluid outputs are recovered from the matching location.

Distillation Tanks are detected but intentionally rejected for now: one operation writes separate fluid products into different vertical tank layers, while a provider job currently has one recoverable output position.

Primary outputs may be items **or fluids**. Fluid products and byproducts are recovered from fluid handlers at the output position.

## Smart doubling

Enable smart doubling on the provider to merge same-pattern pushes into larger fills based on free tank/slot capacity. Mixers and basin feeds can pre-queue enough patterns for bulk refills instead of dripping 1B at a time. Pending dispatches retry every tick while the target is full, and collecting outputs immediately re-wakes crafting.

## Recovery notes

Saw and crushing-controller exits require a Belt or Depot that accepts the ejected items and exposes an item inventory; arbitrary chests or bare ground are not equivalent. Millstones recover from their own inventories. Mechanical Crafters use their planned in-plane adjacent exit, such as a chest, Depot or the provider itself. Goggles / Jade show status, active jobs, smart-doubling state, and the last failure detail.
