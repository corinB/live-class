// 수강신청 식별자 Value Object — UUID 래퍼, 불변
package com.example.liveclass.domain.enrollment;

import java.util.UUID;

public record EnrollmentId(UUID value) {

    public EnrollmentId {
        if (value == null) {
            throw new IllegalArgumentException("EnrollmentId value must not be null");
        }
    }

    public static EnrollmentId of(UUID value) {
        return new EnrollmentId(value);
    }

    public static EnrollmentId newId() {
        return new EnrollmentId(UUID.randomUUID());
    }
}
