---
navigation:
  parent: createpackage:create-package.md
  title: Mechanical Route Tools
  icon: machine_linker
  position: 257
categories:
- tools
item_ids:
- createpackage:machine_linker
- createpackage:mechanical_pattern_converter
- createpackage:mechanical_package_pattern
---

# Mechanical Route Tools

<Row gap="16">
<ItemImage id="machine_linker" scale="4" />
<ItemImage id="mechanical_pattern_converter" scale="4" />
<ItemImage id="mechanical_package_pattern" scale="4" />
</Row>

Create Package uses explicit route marking so it only touches known machine positions during autocrafting.

## Machine Linker

The Machine Linker stores the ordered machine route on a Package Distributor or Basic Package Distributor.

Right-click a distributor to select it. Then right-click route blocks in order: input depot or belt first, deployers and spouts in physical order, and the output depot or belt last. Sneak-right-click a linked machine to remove it. Sneak-right-click the selected distributor to clear the route.

With FTB Ultimine installed, hold the Ultimine selection key and right-click matching machines to batch-link or batch-unlink the selected shape. Batch linking still only stores the selected positions and does not scan the world.

Holding the Machine Linker highlights the currently selected route in the world.

## Mechanical Pattern Converter

The Mechanical Pattern Converter stores a route on the converter item before converting patterns.

Right-click markable route blocks to append them. Sneak-right-click a marked block to remove it. Right-click air to open the converter GUI. Put an AE2 processing pattern or an existing Mechanical Package Pattern in the input slot and press Convert.

The output Mechanical Package Pattern keeps the encoded AE2 pattern and adds the marked route snapshot.

## Mechanical Package Pattern

Mechanical Package Patterns are used by the Advanced Package Distributor. Each pattern carries the original AE2 pattern data plus the Create route that should receive that job.

Holding Shift on the item tooltip shows the saved route names and positions. Holding the item highlights the saved route in the world. You can also right-click markable route blocks while holding the pattern to edit the saved route directly.

Only blocks that the distributor can route through are markable: depots, belts, deployers, and spouts.
