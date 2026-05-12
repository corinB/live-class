// 강의 진행 기간 Value Object — startDate <= endDate 불변
package com.example.liveclass.domain.clazz;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDate;

@Embeddable
@Getter
@EqualsAndHashCode
@ToString
public class ClassPeriod {

    private final LocalDate startDate;
    private final LocalDate endDate;

    protected ClassPeriod() {
        this.startDate = LocalDate.now();
        this.endDate = LocalDate.now();
    }

    private ClassPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new IllegalArgumentException("ClassPeriod startDate must not be null");
        }
        if (endDate == null) {
            throw new IllegalArgumentException("ClassPeriod endDate must not be null");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("ClassPeriod endDate must not be before startDate");
        }
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public static ClassPeriod of(LocalDate startDate, LocalDate endDate) {
        return new ClassPeriod(startDate, endDate);
    }
}
