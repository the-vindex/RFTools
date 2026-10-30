package mcjty.rftools.compat.jei;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.gui.recipes.RecipeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.config.GuiUtils;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Draws the recipe slots the player cannot supply (see {@link MissingIngredients}) in red on HEI's recipe screen, with a
 * "Missing: ..." tooltip, without making the '+' button an error (HEI 4.34.3 blocks the click on any error). The transfer
 * handlers {@link #record} the missing slots per recipe layout when HEI initialises its button; a draw hook paints them.
 * Uses only public API (IGuiIngredient.drawHighlight, the same call HEI's own red slots use) plus the layout's position and
 * HEI's private list of the layouts on screen. Fails soft: if HEI internals differ, nothing is drawn and one line is logged.
 */
public class MissingOverlay {

    private static final Color RED = new Color(1.0f, 0.0f, 0.0f, 0.4f);
    private static final String RECIPES_GUI = "mezz.jei.gui.recipes.RecipesGui";

    private static final Map<IRecipeLayout, Entry> entries = new WeakHashMap<>();
    private static boolean installed;
    private static boolean failed;
    private static Field layoutsField;

    private static class Entry {
        final SortedSet<Integer> slots;
        final List<String> names;

        Entry(SortedSet<Integer> slots, List<String> names) {
            this.slots = slots;
            this.names = names;
        }
    }

    public static synchronized void install() {
        if (!installed) {
            installed = true;
            MinecraftForge.EVENT_BUS.register(new MissingOverlay());
        }
    }

    static synchronized void record(IRecipeLayout layout, Collection<Integer> slots, List<String> names) {
        if (slots.isEmpty()) {
            entries.remove(layout);
        } else {
            entries.put(layout, new Entry(new TreeSet<>(slots), names));
        }
    }

    /** The recipe slots recorded as missing for this layout (empty when none or never seen). */
    public static synchronized SortedSet<Integer> missingFor(IRecipeLayout layout) {
        Entry e = entries.get(layout);
        return e == null ? new TreeSet<>() : Collections.unmodifiableSortedSet(e.slots);
    }

    public static synchronized List<String> namesFor(IRecipeLayout layout) {
        Entry e = entries.get(layout);
        return e == null ? Collections.<String>emptyList() : e.names;
    }

    /** The layouts HEI currently shows on a RecipesGui (private field), or empty. */
    @SuppressWarnings("unchecked")
    static List<Object> layoutsOf(GuiScreen gui) throws ReflectiveOperationException {
        if (layoutsField == null) {
            layoutsField = gui.getClass().getDeclaredField("recipeLayouts");
            layoutsField.setAccessible(true);
        }
        return (List<Object>) layoutsField.get(gui);
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen gui = event.getGui();
        if (failed || gui == null || !RECIPES_GUI.equals(gui.getClass().getName())) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            for (Object o : layoutsOf(gui)) {
                RecipeLayout layout = (RecipeLayout) o;
                Entry e;
                synchronized (MissingOverlay.class) {
                    e = entries.get(layout);
                }
                if (e == null) {
                    continue;
                }
                Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = layout.getItemStacks().getGuiIngredients();
                for (int slot : e.slots) {
                    IGuiIngredient<ItemStack> gi = ingredients.get(slot);
                    if (gi != null) {
                        gi.drawHighlight(mc, RED, layout.getPosX(), layout.getPosY());
                    }
                }
                if (layout.isMouseOver(event.getMouseX(), event.getMouseY()) && !e.names.isEmpty()) {
                    ScaledResolution res = new ScaledResolution(mc);
                    GuiUtils.drawHoveringText(Collections.singletonList("Missing: " + String.join(", ", e.names)),
                            event.getMouseX(), event.getMouseY(), res.getScaledWidth(), res.getScaledHeight(), -1, mc.fontRenderer);
                }
            }
        } catch (Throwable t) {
            failed = true;
            LogManager.getLogger("rftools").warn("JEI missing-ingredient overlay disabled (HEI internals differ): " + t);
        }
    }
}
