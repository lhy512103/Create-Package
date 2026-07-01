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

The Kinetic Pattern Provider is an AE2 pattern provider for one Create machine or one small Create machine unit. It is meant for recipes where a full sequenced assembly route is unnecessary.

Place it on the ME network, set its target side with an AE2 wrench, and put normal AE2 <ItemLink id="ae2:processing_pattern" />s into its pattern slots. By default it uses only the front machine.

Install <ItemLink id="parallel_card" />s to enable multi-machine dispatch. After installing a card, right-click the Kinetic Pattern Provider with a <ItemLink id="machine_linker" /> to select it, then right-click matching machines of the same type as the front machine. One card allows up to 16 active machines, and two cards allow up to 32. The provider dispatches new jobs to idle linked machines before reporting busy.

## Supported Targets

* Deployer: sends the processed item to the deployer's working position and the held item to the deployer.
* Spout: sends the item to the spout working position and the fluid to the spout.
* Mechanical Press: supports depot or belt mode, and basin mode when a basin is below the press.
* Mechanical Mixer: supports basin recipes.
* Mechanical Saw: sets the saw filter from the pattern output and recovers from the saw's fixed output position.
* Millstone: inserts into the millstone and recovers actual output stacks.
* Crushing Wheel: face the crushing wheel controller and recover from the fixed downstream output position.
* Generic item-handler machines: inserts and recovers through the target inventory.

The provider uses fixed machine positions instead of scanning the world. Dropped item entities are not collected. If a saw or crushing wheel drops items into the world, add a belt, depot, or inventory at the expected output position so the provider can recover them.

## Probabilistic Outputs

AE2 processing patterns treat listed outputs as required. Do not list optional byproducts in the pattern output list unless AE2 should wait for them.

The Kinetic Pattern Provider still recovers probabilistic byproducts that actually appear in supported output inventories. If the primary output is probabilistic, it can refill more inputs from AE storage until the primary output arrives, inputs run out, AE cancels the job, or the job times out.

## Smart Doubling

Smart Doubling is a per-provider setting in this mod's GUI. When enabled, the provider accepts another copy of the same active processing pattern only while the target machine can simulate accepting the next input set.

Smart Doubling batches into an already active machine. Parallel Cards run multiple matching machines at the same time.
