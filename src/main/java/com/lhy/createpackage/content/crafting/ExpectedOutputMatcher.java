package com.lhy.createpackage.content.crafting;

import java.util.Objects;
import java.util.function.Function;

public final class ExpectedOutputMatcher {
    private ExpectedOutputMatcher() {
    }

    public static <E, K> boolean matches(K primary, Iterable<E> declaredOutputs, Function<E, K> keyOf, K candidate) {
        if (Objects.equals(primary, candidate)) {
            return true;
        }
        for (E output : declaredOutputs) {
            if (Objects.equals(keyOf.apply(output), candidate)) {
                return true;
            }
        }
        return false;
    }
}