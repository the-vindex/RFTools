package mcjty.rftools.compat.jei;

import org.junit.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.Assert.*;

public class IngredientPickerTest {

    private static final String REFINED = "ic2:ingot@7";
    private static final String IRON = "minecraft:iron_ingot@0";
    private static final String CHEST = "minecraft:chest@0";

    // Iron Chest: 8 x [refined, iron] around a chest (recipe slot 5).
    private static Map<Integer, List<IngredientPicker.Alternative>> ironChest() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        for (int i = 1; i <= 9; i++) {
            slots.put(i, i == 5 ? alts(CHEST) : alts(REFINED, IRON));
        }
        return slots;
    }

    private static List<IngredientPicker.Alternative> alts(String... keys) {
        List<IngredientPicker.Alternative> list = new ArrayList<>();
        for (String k : keys) {
            list.add(new IngredientPicker.Alternative(k, 1));
        }
        return list;
    }

    // Refined iron is made from iron ingots.
    private static final Function<List<String>, Map<String, Integer>> EASE = keys -> IngredientPicker.easeRank(keys,
            k -> k.equals(REFINED) ? Collections.singletonList(Collections.singleton(IRON)) : Collections.emptyList());

    private static Map<String, Long> stock(Object... kv) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        }
        return m;
    }

    private static String chosen(Map<Integer, List<IngredientPicker.Alternative>> slots, Map<Integer, Integer> choice, int slot) {
        return slots.get(slot).get(choice.get(slot)).key;
    }

    private static int count(Map<Integer, List<IngredientPicker.Alternative>> slots, Map<Integer, Integer> choice, String key) {
        int n = 0;
        for (Map.Entry<Integer, Integer> e : choice.entrySet()) {
            if (chosen(slots, choice, e.getKey()).equals(key)) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void availableBeatsJeiOrder() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(IRON, 8, CHEST, 1), EASE);
        assertEquals(8, count(slots, c, IRON));
        assertEquals(CHEST, chosen(slots, c, 5));
    }

    @Test
    public void availableBeatsEase() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(REFINED, 64), EASE);
        assertEquals(8, count(slots, c, REFINED));
    }

    @Test
    public void nothingAvailablePicksTheNonDerived() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(), EASE);
        assertEquals(8, count(slots, c, IRON));
    }

    @Test
    public void demandIsTalliedAcrossSlots() {
        // 5 iron + 3 refined: the first 5 slots take iron, the remaining 3 what is left.
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(IRON, 5, REFINED, 3), EASE);
        assertEquals(5, count(slots, c, IRON));
        assertEquals(3, count(slots, c, REFINED));
    }

    @Test
    public void slotCountsAreDemand() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        slots.put(1, Arrays.asList(new IngredientPicker.Alternative(REFINED, 4), new IngredientPicker.Alternative(IRON, 4)));
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(REFINED, 3, IRON, 4), EASE);
        assertEquals(IRON, chosen(slots, c, 1));
    }

    @Test
    public void mostInStockWhenEaseTies() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        slots.put(1, alts("a@0", "b@0", "c@0"));
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock("a@0", 1, "b@0", 9, "c@0", 5),
                keys -> new HashMap<>());
        assertEquals("b@0", chosen(slots, c, 1));
        c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(), keys -> new HashMap<>());
        assertEquals("JEI order is the last tie-break", "a@0", chosen(slots, c, 1));
    }

    @Test
    public void nothingInStockKeepsJeiOrderInEverySlot() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), null, stock(), keys -> new HashMap<>());
        assertEquals(8, count(slots, c, REFINED));
    }

    @Test
    public void focusBeatsAvailability() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, Integer> c = IngredientPicker.pick(slots, new HashMap<>(), REFINED, stock(IRON, 64), EASE);
        assertEquals(8, count(slots, c, REFINED));
    }

    @Test
    public void memoryBeatsFocusAndInvalidMemoryFallsBack() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, String> remembered = new HashMap<>();
        remembered.put(1, REFINED);
        remembered.put(2, "minecraft:gold_ingot@0");     // no longer an alternative of slot 2
        Map<Integer, Integer> c = IngredientPicker.pick(slots, remembered, IRON, stock(), EASE);
        assertEquals(REFINED, chosen(slots, c, 1));
        assertEquals(IRON, chosen(slots, c, 2));
    }

    @Test
    public void explicitChoicesClaimStockFirst() {
        // Slot 9 remembers iron; with only 8 iron the other slots must leave one for it.
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        Map<Integer, String> remembered = new HashMap<>();
        remembered.put(9, IRON);
        Map<Integer, Integer> c = IngredientPicker.pick(slots, remembered, null, stock(IRON, 7, REFINED, 1), EASE);
        assertEquals(IRON, chosen(slots, c, 9));
        assertEquals(7, count(slots, c, IRON));
        assertEquals(1, count(slots, c, REFINED));
    }

    @Test
    public void easeIsOnlyComputedWhenItDecides() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = ironChest();
        int[] calls = {0};
        IngredientPicker.pick(slots, new HashMap<>(), null, stock(IRON, 8, CHEST, 1), keys -> { calls[0]++; return new HashMap<>(); });
        assertEquals(0, calls[0]);
    }

    @Test
    public void easeRankOnAFakeRecipeGraph() {
        // plate <- ingot; ingot <- dust; dust <- ore (not a sibling); block made from ingot and nugget.
        Map<String, Collection<Set<String>>> recipes = new HashMap<>();
        recipes.put("plate", Collections.singletonList(set("ingot", "hammer")));
        recipes.put("ingot", Arrays.asList(set("dust"), set("block")));
        recipes.put("dust", Collections.singletonList(set("ore")));
        recipes.put("block", Collections.singletonList(set("ingot", "nugget")));
        Map<String, Integer> rank = IngredientPicker.easeRank(Arrays.asList("plate", "ingot", "dust", "block"),
                k -> recipes.getOrDefault(k, Collections.emptyList()));
        assertEquals(0, (int) rank.get("dust"));
        assertEquals(1, (int) rank.get("plate"));
        assertEquals(2, (int) rank.get("ingot"));   // from dust and from block
        assertEquals(1, (int) rank.get("block"));
    }

    private static Set<String> set(String... s) {
        return new HashSet<>(Arrays.asList(s));
    }
}
