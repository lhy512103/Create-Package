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

New jobs go to idle linked machines first.

## Supported targets

* **Deployer**: processed item to the working position, held item into the deployer. A non-consumed held item already placed in the deployer may be omitted from the pattern.
* **Spout**: item to the working position, fluid into the spout
* **Mechanical Press**: depot/belt or basin mode
* **Mechanical Mixer**: basin recipes (items + fluids)
* **Mechanical Saw**: sets filter from the primary item output; recovers from the fixed output side
* **Millstone / Crushing**: recovers actual stacks from supported output inventories
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

Saws, millstones, and crushing setups still need a recoverable belt, depot, or inventory at the machine output. Goggles / Jade show status, active jobs, smart-doubling state, and the last failure detail.
