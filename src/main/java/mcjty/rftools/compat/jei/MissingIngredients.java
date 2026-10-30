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
        // Slots with the fewest alternatives claim first so a flexible slot doesn't starve a rigid one.
        List<Integer> order = new ArrayList<>();
        for (Map.Entry<Integer, List<IngredientPicker.Alternative>> e : slots.entrySet()) {
            if (!e.getValue().isEmpty()) {
                order.add(e.getKey());
            }
        }
        order.sort(Comparator.<Integer>comparingInt(s -> distinctKeys(slots.get(s))).thenComparingInt(s -> s));

        Map<String, Long> left = new HashMap<>(stock);
        SortedSet<Integer> missing = new TreeSet<>();
        for (int slot : order) {
            IngredientPicker.Alternative best = null;
            for (IngredientPicker.Alternative a : slots.get(slot)) {
                long have = left.getOrDefault(a.key, 0L);
                if (have >= a.count && (best == null || have > left.getOrDefault(best.key, 0L))) {
                    best = a;
                }
            }
            if (best == null) {
                missing.add(slot);
            } else {
                left.merge(best.key, (long) -best.count, Long::sum);
            }
        }
        return missing;
    }

    private static int distinctKeys(List<IngredientPicker.Alternative> alts) {
        Set<String> keys = new HashSet<>();
        for (IngredientPicker.Alternative a : alts) {
            keys.add(a.key);
        }
        return keys.size();
    }
}
