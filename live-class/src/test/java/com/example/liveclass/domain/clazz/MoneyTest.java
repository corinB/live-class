// Money VO 불변식 위반(음수 금액, null 통화) 검증 — 순수 JUnit5
package com.example.liveclass.domain.clazz;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MoneyTest {

    @Test
    void of_validAmount_createsMoneySuccessfully() {
        Money money = Money.of(BigDecimal.valueOf(10000), Currency.getInstance("KRW"));
        assertEquals(BigDecimal.valueOf(10000), money.getAmount());
        assertEquals("KRW", money.getCurrency());
    }

    @Test
    void of_zeroAmount_isAllowed() {
        Money money = Money.of(BigDecimal.ZERO, Currency.getInstance("KRW"));
        assertEquals(BigDecimal.ZERO, money.getAmount());
    }

    @Test
    void of_negativeAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.valueOf(-1), Currency.getInstance("KRW")));
    }

    @Test
    void of_nullAmount_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(null, Currency.getInstance("KRW")));
    }

    @Test
    void of_nullCurrency_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> Money.of(BigDecimal.valueOf(1000), null));
    }
}
