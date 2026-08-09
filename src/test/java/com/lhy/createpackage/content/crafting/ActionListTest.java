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
            public CompensationResult compensate() {
                compensateCount.incrementAndGet();
                log.add("undo1");
                return CompensationResult.complete();
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
            public CompensationResult compensate() {
                compensateCount.incrementAndGet();
                log.add("undo2");
                return CompensationResult.complete();
            }

            @Override
            public String describe() {
                return "ok2";
            }
        };
        SupplyAction fail = new SupplyAction() {
            @Override
            public boolean perform(boolean simulate) {
                if (!simulate) {
                    log.add("fail-partial");
                }
                return false;
            }

            @Override
            public CompensationResult compensate() {
                compensateCount.incrementAndGet();
                log.add("undo-fail");
                return CompensationResult.complete();
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
        assertEquals(3, compensateCount.get());
        assertTrue(result.fullyCompensated());
        assertTrue(result.compensationFailures().isEmpty());
        assertEquals(List.of("ok1", "ok2", "fail-partial", "undo-fail", "undo2", "undo1"), log);
    }

    @Test
    void executeCollectsCompensationFailuresAndContinuesRollback() {
        List<String> log = new ArrayList<>();
        SupplyAction earlier = new SupplyAction() {
            @Override
            public boolean perform(boolean simulate) {
                return true;
            }

            @Override
            public CompensationResult compensate() {
                log.add("undo-earlier");
                return CompensationResult.failed("two items remain");
            }

            @Override
            public String describe() {
                return "earlier";
            }
        };
        SupplyAction failed = new SupplyAction() {
            @Override
            public boolean perform(boolean simulate) {
                return false;
            }

            @Override
            public CompensationResult compensate() {
                log.add("undo-failed");
                throw new IllegalStateException("handler vanished");
            }

            @Override
            public String describe() {
                return "failed";
            }
        };

        ActionList.Result result = new ActionList(List.of(earlier, failed)).execute();

        assertFalse(result.success());
        assertFalse(result.fullyCompensated());
        assertEquals(List.of("undo-failed", "undo-earlier"), log);
        assertEquals(2, result.compensationFailures().size());
        assertEquals(1, result.compensationFailures().get(0).actionIndex());
        assertEquals("failed", result.compensationFailures().get(0).action());
        assertTrue(result.compensationFailures().get(0).detail().contains("handler vanished"));
        assertEquals(0, result.compensationFailures().get(1).actionIndex());
        assertEquals("two items remain", result.compensationFailures().get(1).detail());
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
