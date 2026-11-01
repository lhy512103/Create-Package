package com.lhy.createpackage.content.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class GroupIdentityCacheTest {
    @Test
    void resolvesAllMembersOfAGroupWithOneLookup() {
        Object a = new Object(), b = new Object(), c = new Object();
        var cache = new GroupIdentityCache<Object>();
        assertSame(a, cache.representative(b, () -> List.of(a, b, c)));
        assertSame(a, cache.representative(c, () -> { throw new AssertionError("Duplicate group lookup"); }));
        assertSame(a, cache.representative(a, () -> { throw new AssertionError("Duplicate group lookup"); }));
        assertEquals(1, cache.lookups());
    }

    @Test
    void equalButDistinctInventoriesAreNotTheSameGroup() {
        String a = new String("inventory"), b = new String("inventory");
        var cache = new GroupIdentityCache<String>();
        assertNotSame(cache.representative(a, () -> List.of(a)), cache.representative(b, () -> List.of(b)));
        assertEquals(2, cache.lookups());
    }

    @Test
    void emptyOrBrokenGroupsRemainUnknownWithoutRepeatedLookups() {
        Object a = new Object();
        var cache = new GroupIdentityCache<Object>();
        assertNull(cache.representative(a, List::of));
        assertNull(cache.representative(a, () -> { throw new AssertionError("Repeated broken group lookup"); }));
        assertEquals(1, cache.lookups());
        var nextSearch = new GroupIdentityCache<Object>();
        assertSame(a, nextSearch.representative(a, () -> List.of(a)));
    }
}
