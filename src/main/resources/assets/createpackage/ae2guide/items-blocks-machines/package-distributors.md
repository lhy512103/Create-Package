---
navigation:
  parent: createpackage:create-package.md
  title: Create Package Distributors
  icon: package_distributor
  position: 255
categories:
- devices
item_ids:
- createpackage:package_distributor
- createpackage:basic_package_distributor
- createpackage:advanced_package_distributor
---

# Create Package Distributors

<Row gap="16">
<ItemImage id="package_distributor" scale="4" />
<ItemImage id="basic_package_distributor" scale="4" />
<ItemImage id="advanced_package_distributor" scale="4" />
</Row>

Create Package distributors connect AE2 autocrafting to real Create sequenced assembly lines. They supply the base input to the line, fill linked deployers and spouts with their required inputs, and recover the finished result back into the ME network.

The Create machines still perform the actual work. The belts, depots, deployers, spouts, presses, saws, power, and item movement must be built as a working Create line in the world.

## Package Distributor

The Package Distributor is the external target for a normal <ItemLink id="ae2:pattern_provider" />. Place it on the ME network, put a Pattern Provider next to it, and link the Create line with a <ItemLink id="machine_linker" />.

Link order matters:

1. Input depot or belt.
2. Deployers and spouts in the physical assembly order.
3. Output depot or belt.

Use normal AE2 <ItemLink id="ae2:processing_pattern" />s. The pattern output must identify one Create sequenced assembly recipe, and the inputs must include the base item plus the consumed deployer items and spout fluids needed by the recipe.

## Basic Package Distributor

The Basic Package Distributor embeds AE2 pattern-provider storage in the distributor itself. It uses the same linked route as the Package Distributor, but does not need a separate adjacent Pattern Provider.

Put encoded AE2 processing patterns directly into the Basic Package Distributor GUI, then request the craft from AE2.

## Advanced Package Distributor

The Advanced Package Distributor is for multiple saved assembly routes in one block. It does not use the Machine Linker. Instead, each <ItemLink id="mechanical_package_pattern" /> carries its own marked Create machine route.

Use the <ItemLink id="mechanical_pattern_converter" /> to mark a route and convert an AE2 processing pattern into a Mechanical Package Pattern. Put those patterns into the Advanced Package Distributor.

The Advanced Package Distributor can use <ItemLink id="parallel_card" /> upgrades to run multiple disjoint saved routes at the same time.

## Notes

The Incomplete Package Distributor is a transitional sequenced-assembly ingredient used by the mod's test and crafting recipes. It is not a crafting machine by itself.

Distributors simulate all target insertions before committing inputs. If the guide, goggles, or Jade reports a blocked route, fix the linked machines, target inventories, or output recovery position before retrying the craft.
