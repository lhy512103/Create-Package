package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/** Select actual stored keys without rewriting components or reserving the same stock twice. */
public final class MatchingInputResolver {
    private MatchingInputResolver() {
    }

    public record Input<K>(K key, long amount) {
    }

    public static <K> List<Input<K>> resolve(List<Input<K>> requested, Map<K, Long> available,
            BiPredicate<K, K> matches) {
        Map<K, Long> remaining = new HashMap<>(available);
        List<Input<K>> result = new ArrayList<>(requested);
        boolean[] resolved = new boolean[requested.size()];
        // Reserve exact requirements first, so an earlier fuzzy request cannot steal a later exact input.
        for (int i = 0; i < requested.size(); i++) {
            Input<K> input = requested.get(i);
            if (remaining.getOrDefault(input.key(), 0L) >= input.amount()) {
                remaining.compute(input.key(), (key, amount) -> amount - input.amount());
                resolved[i] = true;
            }
        }
        for (int i = 0; i < requested.size(); i++) {
            if (resolved[i]) {
                continue;
            }
            Input<K> input = requested.get(i);
            for (K candidate : available.keySet()) {
                if (remaining.getOrDefault(candidate, 0L) >= input.amount() && matches.test(input.key(), candidate)) {
                    result.set(i, new Input<>(candidate, input.amount()));
                    remaining.compute(candidate, (key, amount) -> amount - input.amount());
                    break;
                }
            }
        }
        return result;
    }
}
