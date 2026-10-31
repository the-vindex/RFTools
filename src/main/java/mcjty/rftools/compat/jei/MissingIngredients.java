package mcjty.rftools.compat.jei;

import java.util.*;

/**
 * Which recipe slots cannot be supplied at all from what the player owns. Pure logic (no Minecraft types), unit tested.
 * Alternatives are the slot's oredict/JEI alternatives ({@link IngredientPicker.Alternative}); a slot is missing when
 * none of them is left in stock once the other slots have taken theirs. Independent of which alternative the picker
 * ends up choosing, so the JEI page preview and the click agree.
 */
public class MissingIngredients {

    /**
     * @param slots recipe slot -> alternatives (slots without alternatives are ignored)
     * @param stock key -> amount owned (inventory + storage)
     * @return the recipe slots that cannot be filled, ascending
     */
    public static SortedSet<Integer> find(Map<Integer, List<IngredientPicker.Alternative>> slots, Map<String, Long> stock) {
        return assign(slots, stock).missing;
    }

    /** One full set: which key each fillable slot takes (the alternative with most stock left) and which slots are missing. */
    public static class Assignment {
        public final Map<Integer, IngredientPicker.Alternative> chosen = new TreeMap<>();
        public final SortedSet<Integer> missing = new TreeSet<>();

        /** key -> total amount the chosen alternatives take, in slot order. */
        public Map<String, Long> totals() {
            Map<String, Long> totals = new LinkedHashMap<>();
            for (IngredientPicker.Alternative a : chosen.values()) {
                totals.merge(a.key, (long) a.count, Long::sum);
            }
            return totals;
        }
    }

    public static Assignment assign(Map<Integer, List<IngredientPicker.Alternative>> slots, Map<String, Long> stock) {
        Assignment result = new Assignment();
        // Slots with the fewest alternatives claim first so a flexible slot doesn't starve a rigid one.
        List<Integer> order = new ArrayList<>();
        for (Map.Entry<Integer, List<IngredientPicker.Alternative>> e : slots.entrySet()) {
            if (!e.getValue().isEmpty()) {
                order.add(e.getKey());
            }
        }
        order.sort(Comparator.<Integer>comparingInt(s -> distinctKeys(slots.get(s))).thenComparingInt(s -> s));

        Map<String, Long> left = new HashMap<>(stock);
        for (int slot : order) {
            IngredientPicker.Alternative best = null;
            for (IngredientPicker.Alternative a : slots.get(slot)) {
                long have = left.getOrDefault(a.key, 0L);
                if (have >= a.count && (best == null || have > left.getOrDefault(best.key, 0L))) {
                    best = a;
                }
            }
            if (best == null) {
                result.missing.add(slot);
            } else {
                result.chosen.put(slot, best);
                left.merge(best.key, (long) -best.count, Long::sum);
            }
        }
        return result;
    }

    private static int distinctKeys(List<IngredientPicker.Alternative> alts) {
        Set<String> keys = new HashSet<>();
        for (IngredientPicker.Alternative a : alts) {
            keys.add(a.key);
        }
        return keys.size();
    }

    /**
     * How much of each wanted key fits into the player's main inventory: first into partial stacks of the same item, then
     * into empty slots (maxStack per slot), in the order of {@code want}.
     *
     * @param room key -> free room in partial stacks of exactly that item
     */
    public static Map<String, Long> fit(Map<String, Long> want, Map<String, Integer> maxStack, Map<String, Long> room, int emptySlots) {
        Map<String, Long> fits = new LinkedHashMap<>();
        int empty = emptySlots;
        for (Map.Entry<String, Long> e : want.entrySet()) {
            long need = e.getValue();
            long got = Math.min(need, room.getOrDefault(e.getKey(), 0L));
            int max = Math.max(1, maxStack.getOrDefault(e.getKey(), 64));
            while (got < need && empty > 0) {
                got += Math.min(max, need - got);
                empty--;
            }
            fits.put(e.getKey(), got);
        }
        return fits;
    }
}
