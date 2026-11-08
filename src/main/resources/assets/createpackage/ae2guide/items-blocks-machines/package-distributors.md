---
navigation:
  parent: createpackage:create-package.md
  title: Package Distributors
  icon: package_distributor
  position: 255
categories:
- devices
item_ids:
- createpackage:package_distributor
- createpackage:basic_package_distributor
- createpackage:advanced_package_distributor
---

# Package Distributors

<Row gap="16">
<ItemImage id="package_distributor" scale="4" />
<ItemImage id="basic_package_distributor" scale="4" />
<ItemImage id="advanced_package_distributor" scale="4" />
</Row>

Package Distributors feed real Create sequenced-assembly lines from AE2. The distributor supplies inputs, waits for Create to process, and returns recovered outputs to the ME network. Belts, depots, deployers, spouts, power, and item movement must still work in the world.

## Package Distributor

External target for a normal <ItemLink id="ae2:pattern_provider" />. Place it on the ME network beside a pattern provider, then link the Create line with a <ItemLink id="machine_linker" />.

Link order:

1. Input depot or belt.
2. Deployers / spouts in physical process order.
3. Output depot or belt.

Place deployers at their normal Create working distance, two blocks above the target depot; mark the deployer block itself in the route.

Use normal AE2 <ItemLink id="ae2:processing_pattern" />s. The primary output must uniquely match a Create sequenced-assembly recipe. Inputs need the base item plus consumed deployer items and spout fluids.

## Basic Package Distributor

Same linking workflow as the normal distributor, but with built-in AE2 pattern slots. No adjacent pattern provider is required.

## Advanced Package Distributor

Routes live on each <ItemLink id="mechanical_package_pattern" />, not on the block. Convert AE2 processing patterns with a <ItemLink id="mechanical_pattern_converter" />, then insert the mechanical patterns into the advanced distributor.

Install <ItemLink id="parallel_card" />s to run multiple non-overlapping saved lines at once.

When several Mechanical Package Patterns encode the same underlying recipe with different routes, the distributor rotates pushes across idle routes. A request must contain multiple crafts to activate multiple lines, and marked depots or processing machines must not be shared between those routes.

## Behavior notes

With AE2 Utility 1.8 or newer, Basic and Advanced Package Distributors accept one NBT Tear Card in their four shared upgrade slots. It occupies a slot that could otherwise hold a supported upgrade. For an ordinary distributor, install the card in the external pattern provider that supplies its jobs.

Output recognition, crafting returns and refill selection honor the card whitelist and AE2 Utility blacklist while preserving actual item data. Each distributor job saves the rule active when it was accepted; rule changes apply to subsequent jobs.

* Supplies are simulated before commit; partial failures roll back as far as targets allow.
* Probabilistic recipes can refill from AE storage when byproducts or empty-output timeouts show the primary has not arrived.
* Cancelling the AE craft releases the distributor job and clears missing-refill notices.
* Goggles / Jade show status, jobs, missing refill materials, and the last failure detail.
