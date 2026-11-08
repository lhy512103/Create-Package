package com.lhy.createpackage.content.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

class MatchingInputResolverTest {
    @Test
    void usesActualVariantWithoutChangingQuantity() {
        var result = MatchingInputResolver.resolve(List.of(input("tool:old", 2)),
                Map.of("tool:new", 3L), MatchingInputResolverTest::sameItem);
        assertEquals(List.of(input("tool:new", 2)), result);
    }

    @Test
    void reservesExactInputsBeforeFuzzySubstitutes() {
        var result = MatchingInputResolver.resolve(List.of(input("tool:old", 2), input("tool:new", 3)),
                Map.of("tool:new", 3L, "tool:other", 2L), MatchingInputResolverTest::sameItem);
        assertEquals(List.of(input("tool:other", 2), input("tool:new", 3)), result);
    }

    @Test
    void cannotReserveTheSameAlternativeTwice() {
        var result = MatchingInputResolver.resolve(List.of(input("tool:a", 2), input("tool:b", 2)),
                Map.of("tool:c", 3L), MatchingInputResolverTest::sameItem);
        assertEquals(List.of(input("tool:c", 2), input("tool:b", 2)), result);
    }

    @Test
    void missingOrDisallowedVariantsStayMissing() {
        var requested = List.of(input("tool:old", 2));
        assertEquals(requested, MatchingInputResolver.resolve(requested, Map.of("tool:new", 3L), Objects::equals));
        assertEquals(requested, MatchingInputResolver.resolve(requested, Map.of("tool:new", 1L),
                MatchingInputResolverTest::sameItem));
        assertEquals(requested, MatchingInputResolver.resolve(requested, Map.of("other:new", 3L),
                MatchingInputResolverTest::sameItem));
    }

    private static MatchingInputResolver.Input<String> input(String key, long amount) {
        return new MatchingInputResolver.Input<>(key, amount);
    }

    private static boolean sameItem(String expected, String actual) {
        return expected.split(":")[0].equals(actual.split(":")[0]);
    }
}
