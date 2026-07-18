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
     * Executes all actions. On the first failure, previously successful actions are compensated in
     * reverse order and the method returns a failure describing the first bad action.
     */
    public Result execute() {
        List<SupplyAction> done = new ArrayList<>(actions.size());
        for (int i = 0; i < actions.size(); i++) {
            SupplyAction action = actions.get(i);
            if (!action.perform(false)) {
                for (int j = done.size() - 1; j >= 0; j--) {
                    done.get(j).compensate();
                }
                return Result.failed(i, action.describe());
            }
            done.add(action);
        }
        return Result.ok();
    }

    public record Result(boolean succeeded, int failedIndex, @Nullable String failedAction) {
        public static Result ok() {
            return new Result(true, -1, null);
        }

        public static Result failed(int index, String action) {
            return new Result(false, index, action);
        }

        public boolean success() {
            return succeeded;
        }
    }
}
