package com.lhy.createpackage.content.crafting;

/**
 * One reversible supply step in a craft plan.
 *
 * <p>{@link #perform(boolean)} must be side-effect free when {@code simulate} is true.
 * {@link #compensate()} reports whether all side effects owned by this action were undone.
 */
public interface SupplyAction {
    boolean perform(boolean simulate);

    /**
     * Undoes a non-simulated perform. Implementations must retain any uncommitted amount when the
     * target only accepts a partial rollback, so a partial rollback cannot be reported as complete.
     */
    default CompensationResult compensate() {
        return CompensationResult.complete();
    }

    /** Short human-readable label used in failure diagnostics. */
    default String describe() {
        return getClass().getSimpleName();
    }

    record CompensationResult(boolean succeeded, String detail) {
        public static CompensationResult complete() {
            return new CompensationResult(true, "");
        }

        public static CompensationResult failed(String detail) {
            return new CompensationResult(false, detail);
        }
    }
}
