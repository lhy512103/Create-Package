---
navigation:
  parent: createpackage:create-package.md
  title: Mechanical Route Tools
  icon: mechanical_pattern_converter
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

These tools mark Create machine routes for Package Distributors and Mechanical Package Patterns. Holding a route tool highlights the current route in-world.

## Shared ordered-route controls

Applies to **Package Distributor** linking and to converter / mechanical pattern routes. Kinetic Pattern Provider links stay set-like (no Append/Edit mark mode).

| Control | Action |
| --- | --- |
| Left-click air | Toggle **Append** / **Edit** |
| Append + right-click | Add mark |
| Append + sneak + right-click | Remove clicked mark |
| Edit + scroll | Select mark |
| Edit + right-click | Replace selected mark |
| Edit + sneak + right-click | Remove selected mark |
| **G** | Auto-route between endpoints (straight corridor; optional same-Y L-shape) |

Auto-route keeps input/output endpoints and process machines that actually feed the corridor. Presses and other passive path machines are skipped. Mechanical Package Patterns in Edit mode can show the expected material for the selected mark when the AE pattern and sequenced recipe resolve.

## Machine Linker

1. Right-click a <ItemLink id="package_distributor" />, <ItemLink id="basic_package_distributor" />, or <ItemLink id="kinetic_pattern_provider" /> to bind.
2. Right-click machines to link; sneak + right-click to unlink.
3. Sneak + right-click the host to clear its stored links (distributor hosts that use stored links).
4. Hold an FTB Ultimine selection to batch-link / batch-unlink matching machines.

Advanced Package Distributors do **not** use the linker: their routes live on Mechanical Package Patterns.

## Mechanical Pattern Converter

1. Mark a route on markable blocks (depot, belt, deployer, spout, …).
2. Right-click air to open the converter UI.
3. Insert up to nine AE2 processing patterns (or existing Mechanical Package Patterns) and convert.

Conversion writes the converter's currently marked route into each output Mechanical Package Pattern while preserving the original AE2 encoded pattern. Patterns can be converted with an empty route and routed later; AE2 will not register a mechanical pattern for autocrafting until it has at least one route mark. Valid mechanical processing patterns can be re-encoded in AE2's Pattern Encoding Terminal while keeping their saved route.

## Mechanical Package Pattern

Stores the original AE2 pattern plus a Create route for the <ItemLink id="advanced_package_distributor" />.

* Tooltip shows encoder, mode, mark count; hold Shift for route coordinates.
* In Edit mode, selected-mark material hints appear when resolvable.
* Right-click markable blocks to edit the saved route while holding the pattern.
* Press **G** to rebuild the saved route between endpoints.
