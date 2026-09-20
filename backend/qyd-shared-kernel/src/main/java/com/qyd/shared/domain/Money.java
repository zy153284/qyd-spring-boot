package com.qyd.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Money convention: DECIMAL(19,2), ISO-4217 currency; timestamps are UTC Instant and IDs are UUID strings.
 */
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        if (amount == null || currency == null) throw new IllegalArgumentException("amount and currency are required");
        amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        if (amount.precision() > 19) throw new IllegalArgumentException("amount exceeds DECIMAL(19,2)");
    }
}
