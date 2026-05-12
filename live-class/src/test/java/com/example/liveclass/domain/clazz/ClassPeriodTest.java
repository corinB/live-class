// ClassPeriod VO 불변식 위반(endDate < startDate) 검증 — 순수 JUnit5
package com.example.liveclass.domain.clazz;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassPeriodTest {

    @Test
    void of_validPeriod_createsPeriodSuccessfully() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        ClassPeriod period = ClassPeriod.of(start, end);
        assertEquals(start, period.getStartDate());
        assertEquals(end, period.getEndDate());
    }

    @Test
    void of_sameDates_isAllowed() {
        LocalDate date = LocalDate.of(2026, 6, 1);
        ClassPeriod period = ClassPeriod.of(date, date);
        assertEquals(date, period.getStartDate());
        assertEquals(date, period.getEndDate());
    }

    @Test
    void of_endDateBeforeStartDate_throwsIllegalArgumentException() {
        LocalDate start = LocalDate.of(2026, 6, 30);
        LocalDate end = LocalDate.of(2026, 6, 1);
        assertThrows(IllegalArgumentException.class, () -> ClassPeriod.of(start, end));
    }

    @Test
    void of_nullStartDate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassPeriod.of(null, LocalDate.of(2026, 6, 30)));
    }

    @Test
    void of_nullEndDate_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> ClassPeriod.of(LocalDate.of(2026, 6, 1), null));
    }
}
