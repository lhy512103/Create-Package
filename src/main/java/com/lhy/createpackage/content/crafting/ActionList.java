package com.lhy.createpackage.content.crafting;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * Ordered supply plan with simulate / execute-with-compensation helpers.
 */
public final class ActionList {
    private final List<SupplyAction> actions;

    public ActionList(List<SupplyAction> actions) {
        this.actions = List.copyOf(actions);
    }

    public List<SupplyAction> actions() {
        return actions;
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public boolean simulate() {
        for (SupplyAction action : actions) {
            if (!action.perform(true)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Executes all actions. On the first failure, the failed action and all previously successful actions
     * are compensated in reverse order. Compensation failures are collected without interrupting the
     * remaining rollback chain.
     */
    public Result execute() {
        List<SupplyAction> attempted = new ArrayList<>(actions.size());
        for (int i = 0; i < actions.size(); i++) {
            SupplyAction action = actions.get(i);
            attempted.add(action);
            if (!action.perform(false)) {
                List<CompensationFailure> compensationFailures = new ArrayList<>();
                for (int j = attempted.size() - 1; j >= 0; j--) {
                    SupplyAction attemptedAction = attempted.get(j);
                    try {
                        SupplyAction.CompensationResult compensation = attemptedAction.compensate();
                        if (!compensation.succeeded()) {
                            compensationFailures.add(new CompensationFailure(j, attemptedAction.describe(),
                                    compensation.detail()));
                        }
                    } catch (RuntimeException exception) {
                        compensationFailures.add(new CompensationFailure(j, attemptedAction.describe(),
                                exception.getClass().getSimpleName() + ": " + exception.getMessage()));
                    }
                }
                return Result.failed(i, action.describe(), compensationFailures);
            }
        }
        return Result.ok();
    }

    public record CompensationFailure(int actionIndex, String action, String detail) {
    }

    public record Result(boolean succeeded, int failedIndex, @Nullable String failedAction,
            List<CompensationFailure> compensationFailures) {
        public Result {
            compensationFailures = List.copyOf(compensationFailures);
        }

        public static Result ok() {
            return new Result(true, -1, null, List.of());
        }

        public static Result failed(int index, String action) {
            return failed(index, action, List.of());
        }

        public static Result failed(int index, String action, List<CompensationFailure> compensationFailures) {
            return new Result(false, index, action, compensationFailures);
        }

        public boolean success() {
            return succeeded;
        }

        public boolean fullyCompensated() {
            return compensationFailures.isEmpty();
        }
    }
}
