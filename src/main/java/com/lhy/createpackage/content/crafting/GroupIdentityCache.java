package com.lhy.createpackage.content.crafting;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** A read-only planning snapshot: discovering one group also memoizes every inventory in it. */
final class GroupIdentityCache<T> {
    private final Map<T, T> representatives = new IdentityHashMap<>();
    private int lookups;

    T representative(T member, Supplier<? extends List<? extends T>> lookup) {
        if (representatives.containsKey(member)) {
            return representatives.get(member);
        }
        lookups++;
        List<? extends T> group = lookup.get();
        T representative = group.isEmpty() ? null : group.getFirst();
        for (T inventory : group) {
            representatives.put(inventory, representative);
        }
        representatives.put(member, representative);
        return representative;
    }

    int lookups() {
        return lookups;
    }
}
