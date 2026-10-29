# How Refined Storage and AE2-UEL pick ingredients on JEI "+" transfer

Date: 2026-10-29. Purpose: design a client-only equivalent for the RFTools fork (crafter / storage-scanner grid).

Sources (exact versions in the player's pack, `/mnt/minecraft-e36/mods/`):

- Refined Storage **1.6.16**: `refinedmods/refinedstorage` tag `v1.6.16` (commit `2ed9fc1f7b95ad2e5d2b59eb76cc4576c2c035e6`), built against JEI 4.15.0.287.
- AE2 Unofficial Extended Life **v0.56.7**: `AE2-UEL/Applied-Energistics-2` tag `v0.56.7` (commit `f42a5778d2b68b5a6c97f179e17f64817d1a7e0e`).
- The pack's JEI is actually **HadEnoughItems 4.34.3** (`HadEnoughItems_1.12.2-4.34.3.jar`). Its `GuiIngredient` was read with `javap`.
- The pack also has **JustEnoughEnergistics 1.12.2-1.0.8** (p455w0rd). It replaces AE2's handler for `ContainerPatternTerm`. Read with `javap`.

All quotes below are from these sources. Some excerpts are trimmed, and the trims are marked `...`.

---

## 0. JEI/HEI background: displayed vs all ingredients

`mezz.jei.gui.ingredients.GuiIngredient` (HEI 4.34.3, javap) works like this:

- `set(List<T> ingredients, IFocus<T> focus)` first expands subtypes with `ingredientHelper.expandSubtypes(...)`, which expands wildcard metas.
  - `allIngredients` gets the **full expanded list, in the order the recipe wrapper gave it**. For ore-dict recipes that is `OreDictionary.getOres` registration order.
  - `displayIngredients` gets **only the focus match** when the player opened JEI via a focus (R/U on an item) and the focus matches this slot. Otherwise it gets `filterOutHidden(all)`.
- `getDisplayedIngredient()` returns `cycleTimer.getCycledItem(displayIngredients)`. This is the item the player sees *right now*, and it rotates about once a second.
- `getAllIngredients()` returns `allIngredients`. It ignores the focus and the cycle.

This difference matters. "Displayed" carries the player's implicit choice: the focus they looked up, or the frame they clicked on. "All" carries only the recipe's order.

`IGuiItemStackGroup.set(slot, List)` can be called again at any time to **replace a slot's alternatives**. AE2-UEL uses this (see §2.3).

---

## 1. Refined Storage 1.6.16

### 1.1 Client: `RecipeTransferHandlerGrid`
File: `src/main/java/com/raoulvdberge/refinedstorage/integration/jei/RecipeTransferHandlerGrid.java`
https://github.com/refinedmods/refinedstorage/blob/v1.6.16/src/main/java/com/raoulvdberge/refinedstorage/integration/jei/RecipeTransferHandlerGrid.java

- The handler is registered for `ContainerGrid` (crafting grid, pattern grid, and so on).
- **The check pass (`doTransfer == false`) only checks the category.** A crafting/pattern grid accepts only `VanillaRecipeCategoryUid.CRAFTING`. A pattern grid in processing mode accepts everything *except* crafting. Anything else returns `ERROR_CANNOT_TRANSFER`, which is `Type.INTERNAL` and hides the button.
- **No availability check.** The handler does not compute missing items, does not grey out the button and does not highlight slots. The client-side grid item list is never consulted.
- **The transfer pass, crafting mode:**
  ```java
  RS.INSTANCE.network.sendToServer(new MessageGridTransfer(recipeLayout.getItemStacks().getGuiIngredients(),
      container.inventorySlots.stream().filter(s -> s.inventory instanceof InventoryCrafting).collect(Collectors.toList())));
  ```
- **The transfer pass, processing-pattern mode:** for every item and fluid slot it sends only **`getDisplayedIngredient().copy()`**, split into inputs and outputs (`MessageGridProcessingTransfer`). That means the cycled or focused item, with its JEI count.
- Side detail: `LAST_TRANSFER = System.currentTimeMillis()`. `GuiBase` then suppresses scrollbar mouse-wheel input for 200 ms (`TRANSFER_SCROLL_DELAY_MS`), so the wheel event does not leak into the grid after a click.

### 1.2 Packet: `MessageGridTransfer`
File: `src/main/java/com/raoulvdberge/refinedstorage/network/MessageGridTransfer.java`

- `toBytes` writes, for each crafting slot, **every** alternative from `ingredient.getAllIngredients()` in JEI order. Nulls are dropped.
- The displayed ingredient is **not** put first, so the JEI focus and cycle are ignored.
- JEI's slot index is the crafting slot index + 1 (`inputs.get(slot.getSlotIndex() + 1)`), because slot 0 is the output.
  ```java
  for (ItemStack possibleStack : ingredient.getAllIngredients()) { if (possibleStack != null) ingredients.add(possibleStack); }
  ```

### 1.3 Server: `NetworkNodeGrid.onRecipeTransfer` (static)
File: `src/main/java/com/raoulvdberge/refinedstorage/apiimpl/network/node/NetworkNodeGrid.java` (around line 344)
https://github.com/refinedmods/refinedstorage/blob/v1.6.16/src/main/java/com/raoulvdberge/refinedstorage/apiimpl/network/node/NetworkNodeGrid.java

1. **Permission:** a crafting grid needs `Permission.EXTRACT`.
2. **Clear the matrix.**
   - Crafting grid: each item is put back into the network (SIMULATE, then PERFORM). Without a network it goes to the player inventory.
   - If any item cannot be put away, the method **aborts the whole transfer**, which leaves the matrix partially cleared.
   - Pattern grid: the slots are simply emptied.
3. **Fill each slot (crafting grid):**
   - **Network first, across all alternatives:** the alternatives are tried in sent order and the first one the network can supply wins:
     ```java
     ItemStack took = network.extractItem(possibility, 1,
         IComparer.COMPARE_NBT | (possibility.getItem().isDamageable() ? 0 : IComparer.COMPARE_DAMAGE), Action.PERFORM);
     ```
   - **Then the player inventory:** only if the network had none of the alternatives. Alternatives are the outer loop and inventory slots the inner loop, with the same NBT/damage flags. The first match is decremented by 1.
   - **If nothing matches, the slot stays empty.** No ghost item is placed.
   - There is no "most stock" preference and no exact-match-first rule. The only ordering is JEI's `getAllIngredients()` order.
4. **NBT and damage:** NBT is always compared. A JEI stack without NBT matches only network items without NBT. Damage is compared only for non-damageable items, so tools and other damageable items match at any durability. Exactly one item is taken per slot, and `maxTransfer` (shift-click) is ignored.
5. **Pattern grid in crafting mode:** each slot gets `possibilities[0]`, the **first alternative in JEI order**, not the displayed one. Nothing is extracted.
6. **Portable grids:** `PortableGrid.onRecipeTransfer` and `TilePortableGrid.onRecipeTransfer` are no-ops.

### 1.4 Memory and preference in RS
- **None.** Nothing records past choices, and there is no "prefer from" setting.
- The pattern grid has an **"Oredict" toggle** (`NetworkNodeGrid.oredictPattern`, NBT `OredictPattern`, then `ItemPattern.setOredict`). It affects only *autocrafting* of the encoded pattern: `CraftingPattern` expands each input to all of its ore-dict equivalents at craft time. It has no effect on which item JEI transfer places.

---

## 2. AE2-UEL v0.56.7

### 2.1 Registration
File: `src/main/java/appeng/integration/modules/jei/JEIPlugin.java`, lines 88-92.

- **Crafting category:** `ContainerCraftingTerm` and `ContainerWirelessCraftingTerminal`.
- **`Constants.UNIVERSAL_RECIPE_TRANSFER_UID`, meaning all categories:** `ContainerPatternTerm`, `ContainerExpandedProcessingPatternTerm` and `ContainerWirelessPatternTerminal`.
- All of them use the same generic `RecipeTransferHandler<T>`.

### 2.2 Client: `RecipeTransferHandler`
File: `src/main/java/appeng/integration/modules/jei/RecipeTransferHandler.java`
https://github.com/AE2-UEL/Applied-Energistics-2/blob/v0.56.7/src/main/java/appeng/integration/modules/jei/RecipeTransferHandler.java

- **INFORMATION and FUEL categories** return `RecipeTransferErrorInternal.INSTANCE`, which hides the button.
- **The check pass** runs only for the crafting category on a crafting terminal: `new JEIMissingItem(container, recipeLayout)`. If it reports an error, the handler returns it. Pattern terminals get no availability check.
- **Pattern terminal mode switch:** when you click "+", the handler first sends `PacketValueConfig("PatternTerminal.CraftMode", "1"/"0")` so the terminal's mode matches the recipe category. A crafting recipe switches it to crafting mode, and anything else switches it to processing mode.
- **What is sent per slot is ordered, and the displayed ingredient comes first:**
  ```java
  final ItemStack displayed = ingredient.getDisplayedIngredient();
  // prefer currently displayed item
  if (displayed != null && !displayed.isEmpty()) list.add(displayed);
  // prefer pure crystals.
  for (ItemStack stack : ingredient.getAllIngredients()) {
      if (stack == null) continue;
      if (Platform.isRecipePrioritized(stack)) list.add(0, stack); else list.add(stack);
  }
  ```
  - The final order is: purified certus, fluix or nether quartz crystals (hard-coded in `Platform.isRecipePrioritized`), then the **displayed (focused or cycled) item**, then all alternatives in JEI order.
  - The displayed item appears twice, which is harmless.
  - This goes out as NBT `"#<slotIndex>"` lists plus an `"outputs"` list of displayed outputs, in a compressed `PacketJEIRecipe`.
  - Only *input* slots that map to a `SlotCraftingMatrix`/`SlotFakeCraftingMatrix` with the same running index are sent.
- **Processing patterns** are encoded the same way: the first entry is the displayed item, and the server uses entry `[0]` as the ghost item (see §2.4).

### 2.3 Client availability check: `JEIMissingItem` (crafting terminals only)
File: `src/main/java/appeng/integration/modules/jei/JEIMissingItem.java`

- **Data source is entirely client-side.** `mergeInventories` builds an `IItemList` from three parts:
  - `ContainerMEMonitorable.items`, the client copy of the ME network contents, kept current by `PacketMEInventoryUpdate`. This copy includes craftable entries that have stack size 0.
  - The player's main inventory (`PlayerMainInvWrapper`).
  - The terminal's own crafting grid (`getInventoryByName("crafting")`).
- **The constructor runs during the check pass, every frame JEI asks.** For each input slot it walks every alternative:
  - **Damageable items, and GregTech tools:** `available.findFuzzy(search, FuzzyMode.IGNORE_ALL)`, so any durability matches. GT tools must also match metadata.
  - **Everything else:** `available.findPrecise(search)` (item, damage and NBT). It counts as available only if `ext.getStackSize() > used.findPrecise(ext).getStackSize()`.
  - The **`used`** list is a per-recipe tally, so two slots that both need the last plank are not both satisfied. Strictly, the tally grows for every matching alternative in a slot, not one per slot. It is approximate.
  - **If any slot has no available alternative, `errored = true`.** The error is `Type.USER_FACING`, so JEI greys the "+" and calls `showError` on hover.
  - The constructor also adds a `CraftableCallBack` tooltip callback (§2.6).
- **`showError(...)` on hover** recomputes at most once per second (the `lastUpdate` throttle) and does the following for each slot:
  - **Red highlight** (`drawHighlight(..., new Color(1,0,0,0.4f))`) when nothing is available and nothing is craftable.
  - **Blue highlight** when it is not in stock but is craftable. It checks `ext.isCraftable()` / `itemStack.isCraftable()` from the client list.
  - **It rewrites the JEI slot's alternatives to only the ones in stock** (or only the craftable ones):
    ```java
    recipeLayout.getItemStacks().set(currentSlot, validStacks);
    ```
    After the player hovers the greyed button, every slot in the JEI view cycles only through items they actually have. `getDisplayedIngredient()` is therefore an in-stock item, and that is exactly what the transfer sends first.
  - **Partial transfer:** when `errored && foundAny`, it forces the JEI button back on:
    ```java
    RecipeTransferButton b = ((RecipeLayout) recipeLayout).getRecipeTransferButton();
    b.init(c, minecraft.player);
    if (errored && foundAny) { tooltipLines.add(...PartialTransfer); b.enabled = true; b.visible = true; }
    ```
    The tooltip lines are "Missing items", "Partial transfer" and "Craftable items (middle-click to craft)".
  - This touches JEI internals (`mezz.jei.gui.recipes.RecipeLayout`, `RecipeTransferButton`, `TooltipRenderer`), not just the API.

### 2.4 Server: `PacketJEIRecipe.serverPacketData`
File: `src/main/java/appeng/core/sync/packets/PacketJEIRecipe.java`
https://github.com/AE2-UEL/Applied-Energistics-2/blob/v0.56.7/src/main/java/appeng/core/sync/packets/PacketJEIRecipe.java

The method runs once for each crafting-matrix slot `x`.

1. **Keep what already fits.** `canUseInSlot(x, current)` returns the current stack if it is `ItemStack.areItemStacksEqual` to *any* sent alternative (exact item, damage, NBT and count). A slot that already holds a valid alternative is left alone, which avoids churn on repeated clicks. Otherwise the old item is inserted back into ME (`Platform.poweredInsert`), which needs the INJECT permission. On a pattern terminal the ghost is simply dropped.
2. **If the slot is empty, try each variant `y` in sent order.** For each variant it tries the sources in this order before moving on to the next variant:
   - **a. ME network, crafting terminal (`useRealItems() == true`).** The variant is checked against the view-cell filter (`ItemViewCell.createFilter(cct.getViewCells())`) and the EXTRACT permission, then `Platform.poweredExtraction(energy, storage, request(size 1), src)` runs, which is precise. If that fails and the item is damageable or a GT tool, it falls back to `getStorageList().findFuzzy(request, FuzzyMode.IGNORE_ALL)`, takes the first entry with stock > 0 and extracts that.
   - **b. ME network, pattern terminal (`useRealItems() == false`).** If `crafting.getCraftingFor(request, ...)` is non-empty (a pattern can make it), the variant is accepted. Otherwise it is accepted if `storage.extractItems(request, SIMULATE, ...)` finds it. The count is kept from the JEI stack.
   - **c. Player inventory.** Used only if the ME step produced nothing for *this variant*:
     ```java
     currentItem = ad.removeSimilarItems(1, this.recipe.get(x)[y], FuzzyMode.IGNORE_ALL, null);  // real
     currentItem = ad.simulateSimilarRemove(count, recipe[x][y], FuzzyMode.IGNORE_ALL, null);     // pattern
     ```
     `ItemComparisonHelper.isFuzzyEqualItem` with `IGNORE_ALL` matches **any durability** for damageable items. For other items it matches **if the two stacks share ANY ore-dict name** (`OreHelper.sameOre`), and otherwise falls back to `isItemEqual`. Inventory matching is therefore looser than network matching, and it can grab an item that shares an ore name but is not in the JEI list.
   - **The order is variant-major, not source-major.** For variant 0 it tries ME and then the inventory, then does the same for variant 1. RS does the opposite: all variants in the network first, then all variants in the inventory.
3. **When nothing is found:**
   - **Crafting terminal:** the slot stays **empty**.
   - **Pattern terminal:** the slot gets **`recipe[x][0].copy()`** as a ghost item, with its JEI count. Because of the client ordering, `[0]` is the purified crystal if there is one, otherwise the **displayed** item.
4. Then `con.onCraftMatrixChanged(...)`. On a pattern terminal in processing mode the output slots are filled from `outputs`.
5. **Count:** a real crafting terminal always takes 1 per slot. A pattern terminal keeps JEI's stack count. `maxTransfer` (shift) is ignored.

### 2.5 Memory and preference in AE2-UEL
- **No memory of past choices.** The only "preferences" are:
  - Purified crystals first (hard-coded).
  - The displayed item first. Because of the JEI focus and cycle, this is effectively "what the player is looking at".
  - **Keep the current slot contents if they are a valid alternative.**
  - The view-cell filter, which skips network items the terminal's view cells hide.
- **The pattern terminal "Substitute" toggle** (`ContainerPatternEncoder.substitute`, encoded as pattern NBT `substitute`) allows alternative inputs *at autocrafting time*. Transfer itself still encodes one concrete item per slot. AE2-UEL also stores `isSubstitute` in the terminal item NBT so the toggle survives.
- **Pattern terminal vs crafting terminal** (summary):
  - Same client handler.
  - The crafting terminal adds the client availability check (`JEIMissingItem`) plus slot narrowing.
  - The pattern terminal adds the auto mode switch, "a pattern exists that crafts it" as an accept rule, keeps counts, and falls back to a ghost `[0]`.

### 2.6 `CraftableCallBack` (tooltip)
File: `src/main/java/appeng/integration/modules/jei/CraftableCallBack.java`

- On a JEI recipe slot tooltip it adds `§c[Missing]` and/or `§1[Craftable]`, using the same merged client list.
- **Middle-click** while that tooltip is shown sends `PacketInventoryAction(AUTO_CRAFT, ...)`, which opens the craft-amount dialog for that item.

### 2.7 JustEnoughEnergistics 1.0.8, present in the pack
`p455w0rd/jee/integration/JEI` (javap):

- At JEI registration it copies the transfer-handler table and **replaces the `ContainerPatternTerm` + "universal recipe transfer handler" entry** with its own `JEI$RecipeTransferHandler`. It then reflects the new table back into `RecipeTransferRegistry.recipeTransferHandlers`.
- Its handler sends **one item per slot**. It uses `getDisplayedIngredient().copy()` when present, otherwise the first of `getAllIngredients()`, and gives `Platform.isRecipePrioritized` items precedence.
- The packet is `PacketJEIPatternRecipe(recipeTag, outputsTag)`.
- It returns `NEEDED_MODE_CRAFTING`/`NEEDED_MODE_PROCESSING` errors when the terminal mode does not match the category, instead of switching the mode automatically.
- **Not runtime-verified:** whether JEE's override is actually active alongside UEL (load order). Either way, the pattern terminal ends up with the displayed item.

---

## 3. Comparison

| | RS 1.6.16 crafting grid | AE2-UEL crafting terminal | AE2-UEL pattern terminal |
|---|---|---|---|
| Alternatives sent | all, JEI order | purified crystals, displayed, all | same (JEE: displayed only) |
| Respects JEI focus/cycle | no | yes (displayed first) | yes |
| Client availability check | none | yes, client ME list + player inv + grid | none |
| Greys button / highlights | no | red = missing, blue = craftable; partial transfer allowed | no |
| Rewrites JEI slot to in-stock items | no | yes (on hover of greyed button) | no |
| Source order | network (all variants), then player inv (all variants) | per variant: ME, then player inv | per variant: craftable-or-stored, then inv |
| Keeps existing valid slot item | no (always clears) | yes | yes |
| Nothing found | empty | empty | ghost `[0]` |
| NBT/damage | NBT exact; damage ignored for damageable | precise; fuzzy for damageable/GT; inventory: any shared ore name | same |
| Memory | none | none | none (Substitute = craft-time only) |

---

## 4. Ideas a client-only implementation (RFTools fork) can copy

1. **Order alternatives by player intent.** Use JEI's `getDisplayedIngredient()` first, because it captures the focus the player looked up (R/U) and the frame they clicked. Then use `getAllIngredients()`. This is AE2's trick, and it costs nothing.
2. **Decide availability on the client** from a client-side copy of the storage contents, the way AE2 uses `ContainerMEMonitorable.items`. RFTools' storage scanner / modular storage GUIs already hold client lists. Merge the player inventory and the current grid contents.
   - Keep a per-recipe "used" tally, so two slots don't both claim the last item.
   - Pick, for each slot, the first alternative (in intent order) with remaining stock. A natural extension that neither mod does is to break ties by most stock.
3. **Narrow the JEI slot** with `recipeLayout.getItemStacks().set(slotIndex, availableStacks)`. Then what the player sees is what will be placed, and `getDisplayedIngredient()` already resolves to an available item. AE2 does this only on hover of a greyed button. It could be done in the check pass whenever availability is known.
4. **Return a `USER_FACING` `IRecipeTransferError`** that draws red (missing) and blue (craftable) slot highlights with `IGuiIngredient.drawHighlight`. AE2 re-enables the button through JEI internals to allow partial transfers, which is fragile across JEI/HEI versions. A cleaner client-only route is to return `null` (button enabled) when partial transfer is acceptable, and draw the highlights from a tooltip or other render hook instead.
5. **Keep a slot that already holds a valid alternative** (AE2's `canUseInSlot`). This avoids churn and keeps the player's manual pick.
6. **Send exactly one resolved item per slot.** A client-only design sends a fully resolved recipe so the server needs no logic, much like JEE/RS processing mode sending `displayed`. RS/AE send the full list and let the server pick.
7. **Throttle recomputation** to about once a second (AE2's `lastUpdate`), because `transferRecipe(doTransfer=false)` and `showError` run every frame.
8. **Mind the 200 ms scroll suppression** after a transfer (RS), which stops the click or wheel event from scrolling the grid.
9. **There is no memory feature to copy from either mod.** A "last used alternative per ore-dict/ingredient set" preference would be new. It could be stored client-side (config/JSON) and inserted between "displayed" and "all" in the ordering.

Matching caveats to decide deliberately:
- RS requires exact NBT.
- AE2's inventory fallback accepts any shared ore name.
- Damageable items should match at any durability in both mods.
