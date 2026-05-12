// Capacity VO 불변식 위반(0 이하 정원) 검증 — 순수 JUnit5
package com.example.liveclass.domain.clazz;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CapacityTest {

    @Test
    void of_validValue_createsCapacitySuccessfully() {
        Capacity capacity = Capacity.of(30);
        assertEquals(30, capacity.getValue());
    }

    @Test
    void of_minimumValue_createsCapacitySuccessfully() {
        Capacity capacity = Capacity.of(1);
        assertEquals(1, capacity.getValue());
    }

    @Test
    void of_zeroValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Capacity.of(0));
    }

    @Test
    void of_negativeValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Capacity.of(-1));
    }
}
