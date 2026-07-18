---
navigation:
  parent: createpackage:create-package.md
  title: Parallel Card
  icon: parallel_card
  position: 258
categories:
- tools
item_ids:
- createpackage:parallel_card
---

# Parallel Card

<ItemImage id="parallel_card" scale="6" />

Upgrade for <ItemLink id="advanced_package_distributor" /> and <ItemLink id="kinetic_pattern_provider" />. Max two cards per machine. Stackable in normal inventory; AE2 upgrade slots still take one per slot.

## Advanced Package Distributor

Increases concurrent non-overlapping Mechanical Package Pattern jobs:

* 0 cards → 1 job
* 1 card → 4 jobs
* 2 cards → 8 jobs

Each card provides 4 job slots by default. Servers can change this with `advancedDistributorParallelCardCapacity`. Jobs never run concurrently on the same or an overlapping physical route.

## Kinetic Pattern Provider

Unlocks same-type parallel machines linked with the <ItemLink id="machine_linker" />:

* 0 cards → front machine only
* 1 card → up to 16 machines
* 2 cards → up to 32 machines
