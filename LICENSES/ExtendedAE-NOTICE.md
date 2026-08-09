# ExtendedAE Resource Notice

The following graphical resource is adapted from **ExtendedAE**:

- Upstream project: https://github.com/GlodBlock/ExtendedAE
- Upstream branch: `1.21.1-neoforge`
- Upstream path: `src/main/resources/assets/ae2/textures/guis/ex_pattern_provider.png`
- Local path: `src/main/resources/assets/ae2/textures/guis/createpackage_extended_kinetic_pattern_provider.png`
- License: GNU Lesser General Public License v3.0; see `LICENSES/ExtendedAE-LGPL-3.0.txt`
- Corresponding upstream source: https://github.com/GlodBlock/ExtendedAE/tree/1.21.1-neoforge

Local modifications consist of renaming the resource for this mod and using it with a separately authored screen-style document adapted to this provider's title, slot semantics, and paged 9x4 pattern layout.

The page-toolbar interaction was informed by ExtendedAE Plus behavior, but no ExtendedAE Plus code or graphical resources were copied. The implementation uses Applied Energistics 2's public `addToLeftToolbar`, `IconButton`, `Icon.ARROW_LEFT`, and `Icon.ARROW_RIGHT` APIs.