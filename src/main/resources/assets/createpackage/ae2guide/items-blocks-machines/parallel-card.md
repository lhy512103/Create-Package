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

Parallel Cards upgrade the <ItemLink id="advanced_package_distributor" /> and <ItemLink id="kinetic_pattern_provider" />.

Each installed card doubles the number of active disjoint mechanical package routes the Advanced Package Distributor can run:

* No cards: 1 active line.
* 1 card: 2 active lines.
* 2 cards: 4 active lines.

Only two cards can be installed. The cards stack in normal inventories, but AE2 upgrade slots still accept one card per slot.

Parallel Cards do not make one physical Create line process two jobs at once. If a new job overlaps a route that is already active, the Advanced Package Distributor waits for that route to free up.

In a Kinetic Pattern Provider, cards unlock linked matching machines:

* No cards: only the front machine is used.
* 1 card: up to 16 active machines.
* 2 cards: up to 32 active machines.

Use a <ItemLink id="machine_linker" /> to select the Kinetic Pattern Provider, then link machines of the same block type as the front target.
