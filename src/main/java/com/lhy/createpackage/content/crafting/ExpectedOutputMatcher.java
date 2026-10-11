package com.lhy.createpackage.content.crafting;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.BiPredicate;

public final class ExpectedOutputMatcher {
    private ExpectedOutputMatcher() {
    }

    public static <E, K> boolean matches(K primary, Iterable<E> declaredOutputs, Function<E, K> keyOf, K candidate) {
        return matches(primary, declaredOutputs, keyOf, candidate, Objects::equals);
    }

    public static <E, K> boolean matches(K primary, Iterable<E> declaredOutputs, Function<E, K> keyOf, K candidate,
            BiPredicate<K, K> matcher) {
        if (matcher.test(primary, candidate)) {
            return true;
        }
        for (E output : declaredOutputs) {
            if (matcher.test(keyOf.apply(output), candidate)) {
                return true;
            }
        }
        return false;
    }
}
