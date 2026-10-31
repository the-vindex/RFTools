package mcjty.rftools.compat.jei;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** The pure parts of the JEI take-set for Astral Sorcery recipes: one full set from storage, oredict variants, inventory room. */
public class TakeSetTest {

    private static List<IngredientPicker.Alternative> alts(String... keys) {
        List<IngredientPicker.Alternative> list = new ArrayList<>();
        for (String k : keys) {
            list.add(new IngredientPicker.Alternative(k, 1));
        }
        return list;
    }

    private static Map<String, Long> map(Object... kv) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        }
        return m;
    }

    // Discovery-altar-like recipe: 4 marble (oredict, two variants in storage), 1 aquamarine, 1 starmetal.
    private static Map<Integer, List<IngredientPicker.Alternative>> recipe() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            slots.put(i, alts("marbleA", "marbleB"));
        }
        slots.put(4, alts("aquamarine"));
        slots.put(5, alts("starmetal"));
        return slots;
    }

    @Test
    public void fullSetTakesOnePerSlotAndPrefersMostStockedVariant() {
        MissingIngredients.Assignment a = MissingIngredients.assign(recipe(), map("marbleA", 2, "marbleB", 50, "aquamarine", 9, "starmetal", 3));
        assertTrue(a.missing.isEmpty());
        assertEquals(map("marbleB", 4, "aquamarine", 1, "starmetal", 1), new HashMap<>(a.totals()));
    }

    @Test
    public void missingSlotsAreReportedAndTheRestIsStillTaken() {
        MissingIngredients.Assignment a = MissingIngredients.assign(recipe(), map("marbleA", 1, "marbleB", 2, "aquamarine", 9));
        assertEquals(new TreeSet<>(Arrays.asList(3, 5)), a.missing);   // only 3 marble, no starmetal
        Map<String, Long> totals = a.totals();
        assertEquals(3L, totals.get("marbleA") + totals.get("marbleB"));
        assertEquals(Long.valueOf(1), totals.get("aquamarine"));
        assertNull(totals.get("starmetal"));
    }

    @Test
    public void secondSetFromWhatIsLeftAfterTheFirst() {
        Map<String, Long> stock = map("marbleA", 0, "marbleB", 8, "aquamarine", 2, "starmetal", 1);
        MissingIngredients.Assignment first = MissingIngredients.assign(recipe(), stock);
        assertTrue(first.missing.isEmpty());
        for (Map.Entry<String, Long> e : first.totals().entrySet()) {
            stock.merge(e.getKey(), -e.getValue(), Long::sum);
        }
        MissingIngredients.Assignment second = MissingIngredients.assign(recipe(), stock);
        assertEquals(Collections.singleton(5), second.missing);   // starmetal ran out
        assertEquals(Long.valueOf(4), second.totals().get("marbleB"));
    }

    @Test
    public void unmatchedSlotIsMissing() {
        Map<Integer, List<IngredientPicker.Alternative>> slots = new HashMap<>();
        slots.put(1, alts("none"));
        assertEquals(Collections.singleton(1), MissingIngredients.assign(slots, map()).missing);
    }

    @Test
    public void fitFillsPartialStacksThenEmptySlotsAndReportsTheRest() {
        Map<String, Long> want = map("marble", 70, "aquamarine", 1, "starmetal", 2);
        Map<String, Integer> max = new HashMap<>();
        max.put("marble", 64);
        max.put("aquamarine", 64);
        max.put("starmetal", 64);
        Map<String, Long> room = map("aquamarine", 5);
        // 2 empty slots: marble needs both (64 + 6), aquamarine goes on its partial stack, starmetal has no room.
        Map<String, Long> fits = MissingIngredients.fit(want, max, room, 2);
        assertEquals(map("marble", 70, "aquamarine", 1, "starmetal", 0), fits);
    }

    @Test
    public void fitRespectsSmallMaxStack() {
        Map<String, Integer> max = new HashMap<>();
        max.put("bucket", 1);
        assertEquals(map("bucket", 2), MissingIngredients.fit(map("bucket", 3), max, map(), 2));
    }
}
