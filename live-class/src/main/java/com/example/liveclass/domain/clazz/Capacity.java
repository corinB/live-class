// 강의 정원 Value Object — value >= 1 불변
package com.example.liveclass.domain.clazz;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class Capacity {

    private final int value;

    protected Capacity() {
        this.value = 1;
    }

    private Capacity(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Capacity value must be at least 1");
        }
        this.value = value;
    }

    public static Capacity of(int value) {
        return new Capacity(value);
    }
}
