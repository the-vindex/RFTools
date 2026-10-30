package mcjty.rftools.compat.jei;

import mcjty.lib.varia.ItemStackList;
import mcjty.rftools.network.RFToolsMessages;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IIngredientType;
import mezz.jei.api.recipe.IRecipeCategory;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.util.*;

/**
 * Client side of the JEI '+' transfer into the RFTools crafting grid: picks, per slot, the alternative the player is most
 * likely to want (see {@link IngredientPicker}) instead of JEI's first one, and remembers how the player last crafted a
 * result ({@link RecipeMemory}). Only the chosen stacks are sent, in the unchanged {@link PacketSendRecipe}.
 */
public class JeiTransfer {

    private static final int MAX_RECIPES_PER_CANDIDATE = 200;

    private static IRecipeRegistry recipeRegistry;
    private static final Map<List<String>, Map<String, Integer>> easeCache = new HashMap<>();
    private static RecipeMemory memory;

    public static void setRecipeRegistry(IRecipeRegistry registry) {
        recipeRegistry = registry;
        easeCache.clear();
    }

    public static String key(ItemStack stack) {
        return stack.getItem().getRegistryName() + "@" + stack.getMetadata();
    }

    private static RecipeMemory memory() {
        if (memory == null) {
            memory = new RecipeMemory(new File(Loader.instance().getConfigDir(), "rftools/client_recipe_memory.json").toPath());
        }
        return memory;
    }

    /**
     * Never returns a JEI error (HEI 4.34.3 would grey out and block the '+' button): missing slots are handed to
     * {@link MissingOverlay}, which draws them red.
     *
     * @param doTransfer false for JEI's page view (button state): only works out what is missing, sends nothing
     */
    public static void transfer(IRecipeLayout recipeLayout, EntityPlayer player, List<ItemStack> storage, BlockPos pos, boolean doTransfer) {
        Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients = recipeLayout.getItemStacks().getGuiIngredients();

        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        Map<String, ItemStack> stacks = new HashMap<>();
        String output = null;
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : guiIngredients.entrySet()) {
            List<ItemStack> all = entry.getValue().getAllIngredients();
            if (!entry.getValue().isInput()) {
                if (output == null && !all.isEmpty() && all.get(0) != null) {
                    output = key(all.get(0));
                }
                continue;
            }
            List<IngredientPicker.Alternative> alts = new ArrayList<>();
            for (ItemStack stack : all) {
                if (stack != null && !stack.isEmpty()) {
                    alts.add(new IngredientPicker.Alternative(key(stack), stack.getCount()));
                    stacks.putIfAbsent(key(stack), stack);
                }
            }
            slots.put(entry.getKey(), alts);
        }

        Map<String, Long> stock = new HashMap<>();
        for (ItemStack stack : player.inventory.mainInventory) {
            addStock(stock, stack);
        }
        for (ItemStack stack : player.inventory.offHandInventory) {
            addStock(stock, stack);
        }
        for (ItemStack stack : storage) {
            addStock(stock, stack);
        }

        SortedSet<Integer> missing = MissingIngredients.find(slots, stock);
        MissingOverlay.record(recipeLayout, missing, missingNames(guiIngredients, missing));
        if (!doTransfer) {
            return;
        }

        // Grid slot i (1..9) is recipe slot i: the same indices go into PacketSendRecipe.
        Map<Integer, String> remembered = new HashMap<>();
        RecipeMemory.Stack[] grid = output == null ? null : memory().get(output);
        if (grid != null) {
            for (int i = 0; i < 9; i++) {
                if (grid[i] != null) {
                    remembered.put(i + 1, grid[i].key());
                }
            }
        }

        String focus = null;
        IFocus<?> f = recipeLayout.getFocus();
        if (f != null && f.getMode() == IFocus.Mode.INPUT && f.getValue() instanceof ItemStack) {
            focus = key((ItemStack) f.getValue());
        }

        Map<Integer, Integer> choice = IngredientPicker.pick(slots, remembered, focus, stock, keys -> easeRank(keys, stacks));

        ItemStackList items = ItemStackList.create(10);
        for (Map.Entry<Integer, Integer> entry : choice.entrySet()) {
            List<ItemStack> all = guiIngredients.get(entry.getKey()).getAllIngredients();
            String chosen = slots.get(entry.getKey()).get(entry.getValue()).key;
            for (ItemStack stack : all) {
                if (stack != null && !stack.isEmpty() && key(stack).equals(chosen)) {
                    items.set(entry.getKey(), stack.copy());
                    break;
                }
            }
        }
        // Whatever is available goes in, also when something is missing.
        RFToolsMessages.INSTANCE.sendToServer(new PacketSendRecipe(items, pos));
    }

    private static List<String> missingNames(Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients, Collection<Integer> missing) {
        Set<String> names = new LinkedHashSet<>();
        for (int slot : missing) {
            IGuiIngredient<ItemStack> gi = guiIngredients.get(slot);
            if (gi == null) {
                continue;
            }
            for (ItemStack stack : gi.getAllIngredients()) {
                if (stack != null && !stack.isEmpty()) {
                    names.add(stack.getDisplayName());
                    break;
                }
            }
        }
        return new ArrayList<>(names);
    }

    /** The stacks in the container's slots that belong to the given inventory. */
    public static List<ItemStack> stacksIn(Container container, Object inventory) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Slot slot : container.inventorySlots) {
            if (slot.inventory == inventory) {
                stacks.add(slot.getStack());
            }
        }
        return stacks;
    }

    private static void addStock(Map<String, Long> stock, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stock.merge(key(stack), (long) stack.getCount(), Long::sum);
        }
    }

    /** Called when the player crafts from the grid: slot 0 holds the result, 1..9 the recipe. */
    public static void recordCraft(IInventory gridInventory) {
        ItemStack result = gridInventory.getStackInSlot(0);
        if (result.isEmpty()) {
            return;
        }
        RecipeMemory.Stack[] grid = new RecipeMemory.Stack[9];
        for (int i = 0; i < 9; i++) {
            ItemStack stack = gridInventory.getStackInSlot(i + 1);
            if (!stack.isEmpty()) {
                grid[i] = new RecipeMemory.Stack(String.valueOf(stack.getItem().getRegistryName()), stack.getMetadata(), stack.getCount());
            }
        }
        memory().record(key(result), grid);
    }

    private static Map<String, Integer> easeRank(List<String> keys, Map<String, ItemStack> stacks) {
        List<String> cacheKey = new ArrayList<>(keys);
        Collections.sort(cacheKey);
        Map<String, Integer> rank = easeCache.get(cacheKey);
        if (rank == null) {
            rank = IngredientPicker.easeRank(keys, k -> inputsOfRecipesFor(stacks.get(k)));
            easeCache.put(cacheKey, rank);
        }
        return rank;
    }

    // The input key sets of (at most MAX_RECIPES_PER_CANDIDATE) JEI recipes, any category, that produce the item.
    @SuppressWarnings("unchecked")
    private static Collection<Set<String>> inputsOfRecipesFor(ItemStack stack) {
        List<Set<String>> result = new ArrayList<>();
        if (recipeRegistry == null || stack == null) {
            return result;
        }
        IFocus<ItemStack> focus = recipeRegistry.createFocus(IFocus.Mode.OUTPUT, stack);
        for (IRecipeCategory category : recipeRegistry.getRecipeCategories(focus)) {
            for (Object wrapper : recipeRegistry.getRecipeWrappers(category, focus)) {
                if (result.size() >= MAX_RECIPES_PER_CANDIDATE) {
                    return result;
                }
                Inputs inputs = new Inputs();
                try {
                    ((IRecipeWrapper) wrapper).getIngredients(inputs);
                } catch (RuntimeException e) {
                    continue;
                }
                result.add(inputs.keys);
            }
        }
        return result;
    }

    /** Collects the item inputs a recipe wrapper declares; everything else is ignored. */
    private static class Inputs implements IIngredients {
        final Set<String> keys = new HashSet<>();

        private void add(Class<?> cls, List<?> list) {
            if (cls != null && ItemStack.class.isAssignableFrom(cls) && list != null) {
                for (Object o : list) {
                    if (o instanceof ItemStack && !((ItemStack) o).isEmpty()) {
                        keys.add(key((ItemStack) o));
                    }
                }
            }
        }

        private void addLists(Class<?> cls, List<? extends List<?>> lists) {
            if (lists != null) {
                for (List<?> list : lists) {
                    add(cls, list);
                }
            }
        }

        @Override public <T> void setInput(IIngredientType<T> type, T input) { add(type.getIngredientClass(), Collections.singletonList(input)); }
        @Override public <T> void setInputs(IIngredientType<T> type, List<T> input) { add(type.getIngredientClass(), input); }
        @Override public <T> void setInputLists(IIngredientType<T> type, List<List<T>> input) { addLists(type.getIngredientClass(), input); }
        @Override public <T> void setOutput(IIngredientType<T> type, T output) { }
        @Override public <T> void setOutputs(IIngredientType<T> type, List<T> outputs) { }
        @Override public <T> void setOutputLists(IIngredientType<T> type, List<List<T>> outputs) { }
        @Override public <T> List<List<T>> getInputs(IIngredientType<T> type) { return Collections.emptyList(); }
        @Override public <T> List<List<T>> getOutputs(IIngredientType<T> type) { return Collections.emptyList(); }
        @Override public <T> void setInput(Class<? extends T> cls, T input) { add(cls, Collections.singletonList(input)); }
        @Override public <T> void setInputs(Class<? extends T> cls, List<T> input) { add(cls, input); }
        @Override public <T> void setInputLists(Class<? extends T> cls, List<List<T>> input) { addLists(cls, input); }
        @Override public <T> void setOutput(Class<? extends T> cls, T output) { }
        @Override public <T> void setOutputs(Class<? extends T> cls, List<T> outputs) { }
        @Override public <T> void setOutputLists(Class<? extends T> cls, List<List<T>> outputs) { }
        @Override public <T> List<List<T>> getInputs(Class<? extends T> cls) { return Collections.emptyList(); }
        @Override public <T> List<List<T>> getOutputs(Class<? extends T> cls) { return Collections.emptyList(); }
    }
}
