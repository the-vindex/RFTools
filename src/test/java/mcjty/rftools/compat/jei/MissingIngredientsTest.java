package mcjty.rftools.compat.jei;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class MissingIngredientsTest {

    private static final String REFINED = "ic2:ingot@7";
    private static final String IRON = "minecraft:iron_ingot@0";
    private static final String CHEST = "minecraft:chest@0";

    private static List<IngredientPicker.Alternative> alts(String... keys) {
        List<IngredientPicker.Alternative> list = new ArrayList<>();
        for (String k : keys) {
            list.add(new IngredientPicker.Alternative(k, 1));
        }
        return list;
    }

    private static Map<Integer, List<IngredientPicker.Alternative>> ironChest() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        for (int i = 1; i <= 9; i++) {
            slots.put(i, i == 5 ? alts(CHEST) : alts(REFINED, IRON));
        }
        return slots;
    }

    private static Map<String, Long> stock(Object... kv) {
        Map<String, Long> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        }
        return m;
    }

    @Test
    public void everythingOwnedMeansNothingMissing() {
        assertTrue(MissingIngredients.find(ironChest(), stock(IRON, 8, CHEST, 1)).isEmpty());
    }

    @Test
    public void alternativesAreSummedAcrossSlots() {
        // 5 iron + 3 refined fill the 8 metal slots (oredict-style: either alternative satisfies a slot).
        assertTrue(MissingIngredients.find(ironChest(), stock(IRON, 5, REFINED, 3, CHEST, 1)).isEmpty());
    }

    @Test
    public void absentIngredientIsReported() {
        assertEquals(new TreeSet<>(Collections.singleton(5)), MissingIngredients.find(ironChest(), stock(IRON, 8)));
    }

    @Test
    public void shortStockReportsTheSlotsThatRunOut() {
        // 6 metal for 8 slots: the last two (in slot order) are missing.
        assertEquals(new TreeSet<>(Arrays.asList(8, 9)), MissingIngredients.find(ironChest(), stock(IRON, 6, CHEST, 1)));
    }

    @Test
    public void nothingOwnedMissesEverySlot() {
        assertEquals(9, MissingIngredients.find(ironChest(), stock()).size());
    }

    @Test
    public void flexibleSlotDoesNotStarveARigidOne() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        slots.put(1, alts(IRON, REFINED));   // could take either
        slots.put(2, alts(IRON));            // only iron
        assertTrue(MissingIngredients.find(slots, stock(IRON, 1, REFINED, 1)).isEmpty());
    }

    @Test
    public void stackCountsMatter() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        slots.put(1, Collections.singletonList(new IngredientPicker.Alternative(IRON, 3)));
        assertEquals(Collections.singleton(1), MissingIngredients.find(slots, stock(IRON, 2)));
        assertTrue(MissingIngredients.find(slots, stock(IRON, 3)).isEmpty());
    }

    @Test
    public void slotsWithoutAlternativesAreIgnored() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        slots.put(1, new ArrayList<>());
        assertTrue(MissingIngredients.find(slots, stock()).isEmpty());
    }
}
