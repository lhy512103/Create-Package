package com.lhy.createpackage.content.crafting;

/**
 * One reversible supply step in a craft plan.
 *
 * <p>{@link #perform(boolean)} must be side-effect free when {@code simulate} is true.
 * {@link #compensate()} undoes a successful non-simulated perform as best as the target allows.
 */
public interface SupplyAction {
    boolean perform(boolean simulate);

    /**
     * Best-effort undo after a successful {@code perform(false)}. Default is no-op for actions that
     * cannot be reversed safely (e.g. saw filter changes).
     */
    default void compensate() {
    }

    /** Short human-readable label used in failure diagnostics. */
    default String describe() {
        return getClass().getSimpleName();
    }
}
