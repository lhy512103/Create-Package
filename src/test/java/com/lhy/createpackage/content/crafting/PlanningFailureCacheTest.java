package com.lhy.createpackage.content.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PlanningFailureCacheTest {
    @Test
    void repeatedRequestsDoNotExtendTheRetryDeadline() {
        var cache = new PlanningFailureCache<String, String>(4, 5);
        cache.remember("patternA", "state", 100, "no_output");
        assertEquals("no_output", cache.get("patternA", "state", 100));
        assertEquals("no_output", cache.get("patternA", "state", 104));
        assertNull(cache.get("patternA", "state", 105));
    }

    @Test
    void anotherPatternAndChangedMachineStateAreNotBlocked() {
        var cache = new PlanningFailureCache<String, String>(4, 5);
        cache.remember("patternA", "stopped", 100, "not_powered");
        assertNull(cache.get("patternB", "stopped", 100));
        assertNull(cache.get("patternA", "running", 100));
    }

    @Test
    void timeGoingBackwardsInvalidatesTheEntry() {
        var cache = new PlanningFailureCache<String, String>(4, 5);
        cache.remember("pattern", "state", 100, "no_output");
        assertNull(cache.get("pattern", "state", 99));
    }

    @Test
    void capacityEvictsLeastRecentlyUsedFailure() {
        var cache = new PlanningFailureCache<String, String>(2, 5);
        cache.remember("a", "state", 100, "a");
        cache.remember("b", "state", 100, "b");
        assertEquals("a", cache.get("a", "state", 100));
        cache.remember("c", "state", 100, "c");
        assertNull(cache.get("b", "state", 100));
        assertEquals("a", cache.get("a", "state", 100));
        assertEquals("c", cache.get("c", "state", 100));
    }
}
