package com.lhy.createpackage.content.crafting;

import java.util.function.IntPredicate;
import java.util.function.IntSupplier;

public final class DynamicSegmentWalker {
    private DynamicSegmentWalker() {
    }

    public static void walk(IntSupplier segmentCount, IntPredicate processSegment) {
        int index = 0;
        while (index < segmentCount.getAsInt()) {
            boolean currentSegmentRemoved = processSegment.test(index);
            if (!currentSegmentRemoved) {
                index++;
            }
        }
    }
}