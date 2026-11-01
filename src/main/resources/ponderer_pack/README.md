# Create Package Ponderer pack

The bundled runtime player loads `assets/createpackage/ponderer/`; `scripts/` is a synchronized authoring copy. Holding W works without installing Ponderer.

Static layout, text, highlights and controls follow the [Ponderer](https://github.com/Nobodiiiii/Ponderer) scene DSL. Animated machine steps are **Create Package extensions**, implemented by `PondererJsonScenes`, and require this mod's player. Stock Ponderer cannot play or round-trip these extensions reliably; do not import and re-export an animated scene without checking the result.

## Authoring

Edit the runtime JSON, then copy the same file to `ponderer_pack/scripts/`. Keep both copies identical. Each segment may set `basePlateSize` and `viewScale`; `encapsulate_bounds` supplies the world dimensions before playback.

After changing a title, text, segment ID or text order, run `python tools/generate_ponder_lang.py` from the repository. Normal Ponder playback reads `assets/createpackage/lang/en_us.json` and `zh_cn.json`, using `<namespace>.ponder.<scene>_<segment>.header` and `.text_1`, `.text_2`, etc.; inline DSL text only supplies editing-mode defaults. The generator preserves other translations and removes obsolete scene keys. `PonderSceneContractTest` checks every normal-mode key in both processed language resources, as well as the synchronized script copies.

Additional steps:

- `create_belt`: `blockPos`, `blockPos2`, `speed`. Creates a complete, straight horizontal belt including controller, length and segment indices.
- `set_kinetic_speed`: `blockPos`, optional `blockPos2`, `speed`.
- `set_deployer_item`: `blockPos`, `item`, optional `count`.
- `create_depot_item` / `remove_depot_item`: `blockPos`, with `item` and optional `count` for insertion.
- `set_millstone_item`: `blockPos`, `item`, optional `count`, `output` (default false). Count zero clears that inventory slot.
- `move_deployer`: `blockPos`, `distance` (+1 extend, -1 retract), `duration`. Non-blocking: use `idle` to wait.
- `create_belt_item`: `blockPos`, `item`, `link`. Creates and holds a tracked item for scripted processing.
- `move_belt_item`: `link`, `position`, `duration`. Blocks while moving along the belt, in controller-relative segments (first segment center = 0.5).
- `set_belt_item_position`: `link`, `position`. Repositions the same stack instantly, used for distributor return-to-input.
- `change_belt_item` / `remove_belt_item`: `link`, with `item` for replacement. Transitional items may include `assemblyRecipe`, `assemblyStep`, `assemblyTotalSteps` to preserve actual Create assembly progress.
- `show_line`: `point`, `point2`, `color`, `duration`. Illustrates direct supply, recovery or route relationships, not physical item transport.
- `indicate_success`: `blockPos`.

Always specify `action` for `show_controls`. Installing a card by clicking requires `action: "right"` and `whileSneaking: true`; inserting patterns is a GUI operation.

A text step may set `keybind: "key.ftbultimine"` and include exactly one `%s` in both localized strings. The player supplies the current configured key through Ponder's format-parameter API in normal and editing modes. When the optional mod is absent or unbound, a localized fallback is shown. Do not use `whileCTRL` as a stand-in for the Ultimine key: selecting a shape, batch linking and batch unlinking use different modifier combinations.

Special-target tutorials live in `kinetic_crushing_wheels.json`, `kinetic_mechanical_crafters.json`, `kinetic_machine_workstations.json` and `kinetic_batch_linking.json`. Keep the runtime registration list, authoring copies and contract-test file list synchronized when adding a tutorial. Invisible crushing controllers are outlined only as teaching aids. Crafter examples follow the current adapter's automatic layout/arrow handling rather than vanilla manual setup.

These scenes script the real machines' visual behavior and material paths; they do not emulate an AE2 server, crafting CPU or inventory network. Layouts include mechanical power connections, and text calls out the powered ME network, channels, storage and crafting CPU required in a real build. Validate runtime processing separately from scene playback.
