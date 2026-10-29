# Smarter JEI transfer for the crafting grid — design (2026-10-29)

Fork of McJtyMods/RFTools `1.12` (= 7.73, the version in the player's IBP pack). Player decisions: 2026-10-29.
Status: **draft, awaiting player review**.

## Constraints

- **Client-only patch.** The player plays multiplayer (`minecraft.brage.info`), and the server runs stock RFTools 7.73.
  No packet format, no server logic and no registry changes. Keep the version string `1.12-7.73` so the FML handshake
  matches the server.
- Keep the diff minimal and contained (same rule as the Schematica fork).

## Problem (read in source)

`RFToolsJeiPlugin.transferRecipe` (shared by the Storage Scanner, Modular Storage and Remote Storage handlers) puts
`getAllIngredients().get(0)` into each grid slot: the first ore-dict alternative, regardless of what the player owns.
Iron Chest → refined iron. The craft code (`StorageCraftingTools`) then crafts strictly first and only tries a loose
ore-dict match after that. The source it draws from is **the player's inventory + routable storages**
(`StorageScannerTileEntity.craft`). In practice the grid holds an item the player never has.

Not in scope (player decision): greying out JEI's `+` when items are missing. Running out of a stored recipe is normal.

## Behaviour

### 1. Pick what the player owns

For each slot, choose from JEI's alternatives in this order:

1. **Remembered choice** (§2), if it is one of this slot's alternatives.
2. **Available**: the player's inventory + the scanner's known contents have enough of it for this recipe, counting
   demand across slots. For the scanner the contents are `GuiStorageScanner.fromServer_inventory`, i.e. the selected view.
   For Modular/Remote Storage, the GUI's own item list.
3. **Easiest to craft** (§3).
4. **Most in stock.**
5. JEI's order: today's behaviour, the last tie-break.

The chosen stacks go into the unchanged `PacketSendRecipe`.

### 2. Recipe memory (client config)

- **Recorded when the player crafts from the grid** (`GuiCraftingGrid.craft(n)`, client side). That is the moment the
  player has accepted the grid as it is, including manual swaps. Key: the result item (`id@meta`); value: the 9 grid
  stacks (id, meta, count; no NBT).
- **Applied on JEI transfer** when the recipe's output has an entry. Each slot uses its remembered stack if that stack is
  one of the slot's alternatives; otherwise the slot falls back to §1 steps 2–5. A recipe change in the pack only
  invalidates the slots that changed.
- **File:** `config/rftools/client_recipe_memory.json`, one per client, shared by all worlds and servers. It is written
  after each craft (temp file + atomic move). A corrupt file is logged once and treated as empty.
- Separate from RFTools' existing "store recipe" list, which is per block and lives on the server. That list is left
  untouched.

### 3. "Easiest to craft": derived-sibling rule

Alternative A is **derived** if a JEI recipe (any category: crafting, furnace, machines) that produces A consumes another
alternative B from the same slot. Then B ranks above A, so iron ingot beats refined iron. Rank = how many siblings an
alternative is derived from (0 first).

Performance: only the slot's alternatives are looked up (JEI output focus, usually 2–10 candidates). Each lookup
inspects at most 200 recipes. The result is cached per alternative set (key: sorted item keys) for the session and
cleared on JEI reload. JEI calls the handler on page view too (`doTransfer=false`), so the pick logic only runs when
`doTransfer` is true.

### For reference: AE2 / Refined Storage (from memory, not verified)

Both send **all** alternatives per slot to the server, and the server fills the grid with the first one actually present in
the network or the player's inventory. That needs a server change; the closest we can get client-only is §1 step 2.

## Testing

Dev Minecraft instance (Overseer repo `scripts/run-dev`-style client, never the e36 game dir) with the patched rftools +
mcjtylib + IC2 (for refined iron), driven by the Overseer E2E harness:

- Iron Chest, storage has only iron ingots → the grid gets iron ingots.
- Storage has only refined iron → refined iron (availability beats ease).
- Neither → iron ingot (ease).
- Craft once after swapping a slot by hand → memory file entry; the next transfer reproduces the swap.
- Remembered stack no longer valid for the slot → that slot falls back, the others keep the memory.
- Items only in the player's inventory count as available.
- Joining a server with stock 7.73 still works (handshake). Checked on the real server, once, by the player.

Unit tests: pick order, demand counting, memory load/save/corrupt file.

## Open for the plan

- Build: Docker with the original ForgeGradle 2.3 / Java 8 toolchain, like the Schematica fork. Repo: local clone in
  `~/projects/minecraft/RFTools`, branch `scanner-jei`. Creating a GitHub fork (the-vindex/RFTools) needs the player's OK.
- Deploy: add the jar to `scripts/deploy-e36.sh` in the Overseer repo (replacing stock rftools, backed up), as for
  Schematica?
