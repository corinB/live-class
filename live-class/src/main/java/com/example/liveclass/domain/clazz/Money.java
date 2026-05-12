// 수강료 Value Object — amount(BigDecimal) + currency(Currency), 음수 불허
package com.example.liveclass.domain.clazz;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Currency;

@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class Money {

    private final BigDecimal amount;
    private final String currency;

    protected Money() {
        this.amount = BigDecimal.ZERO;
        this.currency = "KRW";
    }

    private Money(BigDecimal amount, Currency currency) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount must be zero or positive");
        }
        if (currency == null) {
            throw new IllegalArgumentException("Money currency must not be null");
        }
        this.amount = amount;
        this.currency = currency.getCurrencyCode();
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public Currency getCurrencyAsObject() {
        return Currency.getInstance(currency);
    }
}
