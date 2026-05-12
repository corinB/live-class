// 강의 식별자 Value Object — UUID 래퍼, 불변
package com.example.liveclass.domain.clazz;

import java.util.UUID;

public record ClassId(UUID value) {

    public ClassId {
        if (value == null) {
            throw new IllegalArgumentException("ClassId value must not be null");
        }
    }

    public static ClassId of(UUID value) {
        return new ClassId(value);
    }

    public static ClassId newId() {
        return new ClassId(UUID.randomUUID());
    }
}
