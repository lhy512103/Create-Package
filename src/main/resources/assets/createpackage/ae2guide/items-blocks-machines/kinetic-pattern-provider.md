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

* **Deployer**: processed item to the working position, held item into the deployer
* **Spout**: item to the working position, fluid into the spout
* **Mechanical Press**: depot/belt or basin mode
* **Mechanical Mixer**: basin recipes (items + fluids)
* **Mechanical Saw**: sets filter from the primary item output; recovers from the fixed output side
* **Millstone / Crushing**: recovers actual stacks from supported output inventories
* **CreateMoreMachines**: same roles by registry-id suffix, using standard item/fluid capabilities

Primary outputs may be items **or fluids**. Fluid products and byproducts are recovered from fluid handlers at the output position.

## Smart doubling

Enable smart doubling on the provider to merge same-pattern pushes into larger fills based on free tank/slot capacity. Mixers and basin feeds can pre-queue enough patterns for bulk refills instead of dripping 1B at a time. Pending dispatches retry every tick while the target is full, and collecting outputs immediately re-wakes crafting.

## Recovery notes

Saws, millstones, and crushing setups still need a recoverable belt, depot, or inventory at the machine output. Goggles / Jade show status, active jobs, smart-doubling state, and the last failure detail.
