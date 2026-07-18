package com.lhy.createpackage.content.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ActionListTest {
    @Test
    void executeCompensatesPreviousActionsOnFailure() {
        List<String> log = new ArrayList<>();
        AtomicInteger compensateCount = new AtomicInteger();

        SupplyAction ok1 = new SupplyAction() {
            @Override
            public boolean perform(boolean simulate) {
                if (!simulate) {
                    log.add("ok1");
                }
                return true;
            }

            @Override
            public void compensate() {
                compensateCount.incrementAndGet();
                log.add("undo1");
            }

            @Override
            public String describe() {
                return "ok1";
            }
        };
        SupplyAction ok2 = new SupplyAction() {
            @Override
            public boolean perform(boolean simulate) {
                if (!simulate) {
                    log.add("ok2");
                }
                return true;
            }

            @Override
            public void compensate() {
                compensateCount.incrementAndGet();
                log.add("undo2");
            }

            @Override
            public String describe() {
                return "ok2";
            }
        };
        SupplyAction fail = new SupplyAction() {
            @Override
            public boolean perform(boolean simulate) {
                return false;
            }

            @Override
            public String describe() {
                return "fail";
            }
        };

        ActionList list = new ActionList(List.of(ok1, ok2, fail));
        // simulate only checks perform(true); fail action always returns false
        assertFalse(list.simulate());
        ActionList.Result result = list.execute();
        assertFalse(result.success());
        assertEquals(2, result.failedIndex());
        assertEquals("fail", result.failedAction());
        assertEquals(2, compensateCount.get());
        assertEquals(List.of("ok1", "ok2", "undo2", "undo1"), log);
    }

    @Test
    void executeSucceedsWhenAllActionsPass() {
        AtomicInteger performed = new AtomicInteger();
        SupplyAction action = simulate -> {
            if (!simulate) {
                performed.incrementAndGet();
            }
            return true;
        };
        ActionList list = new ActionList(List.of(action, action));
        assertTrue(list.simulate());
        ActionList.Result result = list.execute();
        assertTrue(result.success());
        assertEquals(2, performed.get());
    }
}
