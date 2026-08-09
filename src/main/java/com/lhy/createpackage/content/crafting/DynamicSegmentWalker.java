package com.lhy.createpackage.content.crafting;

import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

/** Iterates a mutable segment list without skipping an element after removal. */
public final class DynamicSegmentWalker {
    private DynamicSegmentWalker() {
    }

    public static void walk(IntSupplier segmentCount, IntPredicate processSegment) {
        int index = 0;
        while (index < segmentCount.getAsInt()) {
            if (!processSegment.test(index)) {
                index++;
            }
        }
    }
}