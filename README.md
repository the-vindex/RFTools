# RFTools

A set of blocks and items to help with RF in general.

Note! This mod is outdated and only for 1.12. For 1.16 and higher you should check RFToolsBase, RFToolsUtility, ...

## This fork (the-vindex/RFTools, branch `scanner-jei`)

Client-only change on top of 7.73: when JEI's `+` fills the crafting grid of the Storage Scanner, Modular Storage or Remote
Storage, each slot gets the alternative the player is likely to want instead of JEI's first ore-dictionary entry:
remembered choice, JEI focus, available in inventory + storage, easiest to craft, most in stock, JEI order. Crafting
from the grid remembers the grid per result in `config/rftools/client_recipe_memory.json`. Design: `rftools/docs/` in local-minecraft-mods-forks. No packet, server or registry changes; the version stays `1.12-7.73`
so a client with this jar joins servers running stock 7.73.

### Building

Build tooling, docs, in-game tests and deploy for this fork live in
the private superproject `local-minecraft-mods-forks` (`~/projects/local-minecraft-mods-forks`) (`rftools/`: `scripts/build.sh rftools` runs the original toolchain —
ForgeGradle 2.3, Java 8 — in Docker, and installs the pack's compile-only jars into `local-maven/`, which is git-ignored and
must not be `libs/`: ForgeGradle 2 puts every file there on the compile classpath and a release jar would shadow the
deobfuscated one).

Build changes versus upstream:
- `build.gradle`: https Forge maven, `jcenter()` and the CurseGradle plugin/upload removed; the dead mod mavens
  (tehnut, progwml6, k-4u, mcmoddev, elytradev) replaced by modmaven.dev, Hwyla from CurseMaven, HEI from `local-maven/`
  instead of JEI 4.8.5; JUnit 4.12 for the tests.
- `gradle.properties`: Forge `14.23.5.2846` -> `14.23.5.2847` (with the current ForgeGradle 2.3 snapshot, 2846's binary
  patches fail with "binary discrepency ... LayerHeldItem"; compile target only), `hei_version` added.

The built jar holds the same classes as the official 7.73 jar plus the new ones in `mcjty/rftools/compat/jei/`
(`IngredientPicker`, `RecipeMemory`, `JeiTransfer`). Neither jar is signed.
