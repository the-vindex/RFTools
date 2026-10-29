package mcjty.rftools.compat.jei;

import java.util.*;
import java.util.function.Function;

/**
 * Chooses one alternative per recipe slot for a JEI transfer. Pure logic (no Minecraft types) so it can be unit tested.
 * Items are identified by keys ("id@meta"). Order per slot:
 * 1. remembered choice, 2. JEI focus, 3. available (stock minus what earlier slots already claimed),
 * 4. easiest to craft (lowest ease rank), 5. most in stock, 6. JEI order.
 */
public class IngredientPicker {

    public static class Alternative {
        public final String key;
        public final int count;

        public Alternative(String key, int count) {
            this.key = key;
            this.count = Math.max(1, count);
        }
    }

    /**
     * @param slots      recipe slot -> alternatives in JEI order
     * @param remembered recipe slot -> remembered key (may be missing for a slot)
     * @param focus      the key of the JEI input focus, or null
     * @param stock      key -> amount the player has (inventory + storage)
     * @param ease       ranks a slot's alternative keys (lower is easier); only called when it decides something
     * @return recipe slot -> index of the chosen alternative (slots without alternatives are left out)
     */
    public static Map<Integer, Integer> pick(Map<Integer, List<Alternative>> slots, Map<Integer, String> remembered, String focus,
                                             Map<String, Long> stock, Function<List<String>, Map<String, Integer>> ease) {
        Map<Integer, Integer> choice = new TreeMap<>();
        Map<String, Long> demand = new HashMap<>();
        SortedMap<Integer, List<Alternative>> sorted = new TreeMap<>(slots);

        // Explicit choices first so they claim their stock before the availability pass.
        for (Map.Entry<Integer, List<Alternative>> entry : sorted.entrySet()) {
            List<Alternative> alts = entry.getValue();
            int idx = indexOf(alts, remembered.get(entry.getKey()));
            if (idx < 0) {
                idx = indexOf(alts, focus);
            }
            if (idx >= 0) {
                choice.put(entry.getKey(), idx);
                claim(demand, alts.get(idx));
            }
        }

        for (Map.Entry<Integer, List<Alternative>> entry : sorted.entrySet()) {
            List<Alternative> alts = entry.getValue();
            if (alts.isEmpty() || choice.containsKey(entry.getKey())) {
                continue;
            }
            List<Integer> candidates = new ArrayList<>();
            for (int i = 0; i < alts.size(); i++) {
                Alternative a = alts.get(i);
                if (left(stock, demand, a.key) >= a.count) {
                    candidates.add(i);
                }
            }
            if (candidates.isEmpty()) {
                for (int i = 0; i < alts.size(); i++) {
                    candidates.add(i);
                }
            }
            int best = candidates.get(0);
            if (candidates.size() > 1) {
                List<String> keys = new ArrayList<>();
                for (Alternative a : alts) {
                    keys.add(a.key);
                }
                Map<String, Integer> rank = ease.apply(keys);
                for (int i : candidates) {
                    if (better(alts.get(i), alts.get(best), rank, stock, demand)) {
                        best = i;
                    }
                }
            }
            choice.put(entry.getKey(), best);
            claim(demand, alts.get(best));
        }
        return choice;
    }

    // Strictly better: easier, else more left in stock; ties keep the earlier (JEI order) candidate.
    private static boolean better(Alternative a, Alternative b, Map<String, Integer> rank, Map<String, Long> stock, Map<String, Long> demand) {
        int ra = rank.getOrDefault(a.key, 0);
        int rb = rank.getOrDefault(b.key, 0);
        if (ra != rb) {
            return ra < rb;
        }
        // Stock already claimed by earlier slots doesn't count; below zero is just "none".
        return Math.max(0, left(stock, demand, a.key)) > Math.max(0, left(stock, demand, b.key));
    }

    private static long left(Map<String, Long> stock, Map<String, Long> demand, String key) {
        return stock.getOrDefault(key, 0L) - demand.getOrDefault(key, 0L);
    }

    private static void claim(Map<String, Long> demand, Alternative a) {
        demand.merge(a.key, (long) a.count, Long::sum);
    }

    private static int indexOf(List<Alternative> alts, String key) {
        if (key == null) {
            return -1;
        }
        for (int i = 0; i < alts.size(); i++) {
            if (alts.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Derived-sibling rule: an alternative is derived from a sibling when a recipe producing it consumes that sibling.
     * Rank = number of siblings it is derived from (0 = not derived, easiest).
     *
     * @param inputsOfRecipesFor key -> the input key sets of the recipes that produce it
     */
    public static Map<String, Integer> easeRank(List<String> keys, Function<String, Collection<Set<String>>> inputsOfRecipesFor) {
        Map<String, Integer> rank = new HashMap<>();
        for (String a : new LinkedHashSet<>(keys)) {
            Set<String> derivedFrom = new HashSet<>();
            for (Set<String> inputs : inputsOfRecipesFor.apply(a)) {
                for (String b : keys) {
                    if (!b.equals(a) && inputs.contains(b)) {
                        derivedFrom.add(b);
                    }
                }
            }
            rank.put(a, derivedFrom.size());
        }
        return rank;
    }
}
