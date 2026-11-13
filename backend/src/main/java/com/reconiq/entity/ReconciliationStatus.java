package com.reconiq.entity;

/**
 * Outcome of comparing one order/transaction pair.
 *
 * <p>Matching rule: an order is MATCHED only when the Order ID matches,
 * the Transaction ID is unique and the amount is exactly equal.</p>
 */
public enum ReconciliationStatus {
    MATCHED,
    UNMATCHED,
    AMOUNT_MISMATCH,
    DUPLICATE,
    MISSING,
    FAILED,
    PENDING
}
