package com.lhy.createpackage.content.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DynamicSegmentWalkerTest {
    @Test
    void keepsCurrentIndexWhenProcessedSegmentIsRemoved() {
        List<String> segments = new ArrayList<>(List.of("main", "slag", "residue"));
        List<String> visited = new ArrayList<>();

        DynamicSegmentWalker.walk(segments::size, index -> {
            visited.add(segments.get(index));
            segments.remove(index);
            return true;
        });

        assertEquals(List.of("main", "slag", "residue"), visited);
        assertEquals(List.of(), segments);
    }

    @Test
    void advancesPastSegmentsThatAreNotRemoved() {
        List<String> segments = new ArrayList<>(List.of("input", "main", "slag"));
        List<String> visited = new ArrayList<>();

        DynamicSegmentWalker.walk(segments::size, index -> {
            String segment = segments.get(index);
            visited.add(segment);
            if (!segment.equals("input")) {
                segments.remove(index);
                return true;
            }
            return false;
        });

        assertEquals(List.of("input", "main", "slag"), visited);
        assertEquals(List.of("input"), segments);
    }
}