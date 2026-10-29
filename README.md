# RFTools

A set of blocks and items to help with RF in general.

Note! This mod is outdated and only for 1.12. For 1.16 and higher you should check RFToolsBase, RFToolsUtility, ...

## This fork (the-vindex/RFTools, branch `scanner-jei`)

Client-only change on top of 7.73: when JEI's `+` fills the crafting grid of the Storage Scanner, Modular Storage or Remote
Storage, each slot gets the alternative the player is likely to want instead of JEI's first ore-dictionary entry:
remembered choice, JEI focus, available in inventory + storage, easiest to craft, most in stock, JEI order. Crafting
from the grid remembers the grid per result in `config/rftools/client_recipe_memory.json`. Design:
`docs/2026-10-29-scanner-jei-transfer-design.md`. No packet, server or registry changes; the version stays `1.12-7.73`
so a client with this jar joins servers running stock 7.73.

### Building (Docker, original toolchain)

Upstream's toolchain (Gradle 2.7 wrapper, ForgeGradle 2.3, Java 8) is kept and run in a container:

    scripts/docker-build.sh      # -> build/libs/rftools-1.12-7.73.jar (runs the unit tests too)

- `docker/Dockerfile`: `eclipse-temurin:8-jdk`. The Gradle cache lives in the named volume `rftools-gradle`; the build
  runs as the calling user.
- `scripts/fetch-libs.sh` installs the pack's Had Enough Items 4.34.3 (the JEI fork the pack runs; its `IIngredients`
  API differs from JEI 4.8) from a local e36 instance (`/mnt/minecraft-e36/mods`, override with `E36_MODS`) into the
  git-ignored `local-maven/`. Not `libs/`: ForgeGradle 2 puts every file there on the compile classpath, and the release
  jar would shadow the deobfuscated one.

Build changes versus upstream:
- `build.gradle`: https Forge maven, `jcenter()` and the CurseGradle plugin/upload removed; the dead mod mavens
  (tehnut, progwml6, k-4u, mcmoddev, elytradev) replaced by modmaven.dev, Hwyla from CurseMaven, HEI from `local-maven/`
  instead of JEI 4.8.5; JUnit 4.12 for the tests.
- `gradle.properties`: Forge `14.23.5.2846` -> `14.23.5.2847` (with the current ForgeGradle 2.3 snapshot, 2846's binary
  patches fail with "binary discrepency ... LayerHeldItem"; compile target only), `hei_version` added.

The built jar holds the same classes as the official 7.73 jar plus the new ones in `mcjty/rftools/compat/jei/`
(`IngredientPicker`, `RecipeMemory`, `JeiTransfer`). Neither jar is signed.
