package com.lhy.createpackage.content.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExpectedOutputMatcherTest {
    @Test
    void acceptsPrimaryAndDeclaredByproducts() {
        List<String> outputs = List.of("ingot", "slag");

        assertTrue(ExpectedOutputMatcher.matches("ingot", outputs, value -> value, "ingot"));
        assertTrue(ExpectedOutputMatcher.matches("ingot", outputs, value -> value, "slag"));
    }

    @Test
    void rejectsUndeclaredCrucibleInput() {
        List<String> outputs = List.of("ingot", "slag");

        assertFalse(ExpectedOutputMatcher.matches("ingot", outputs, value -> value, "unmelted_ore"));
    }

    @Test
    void scopedMatcherAppliesToPrimaryAndByproductsOnly() {
        var outputs = List.of("ingot:plain", "slag:plain");
        java.util.function.BiPredicate<String, String> byItem =
                (expected, actual) -> expected.split(":")[0].equals(actual.split(":")[0]);
        assertTrue(ExpectedOutputMatcher.matches("ingot:plain", outputs, value -> value, "ingot:named", byItem));
        assertTrue(ExpectedOutputMatcher.matches("ingot:plain", outputs, value -> value, "slag:named", byItem));
        assertFalse(ExpectedOutputMatcher.matches("ingot:plain", outputs, value -> value, "ore:named", byItem));
        assertFalse(ExpectedOutputMatcher.matches("ingot:plain", outputs, value -> value, "ingot:named"));
    }
}
