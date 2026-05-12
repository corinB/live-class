// 요청된 강의 ID 가 DB 에 존재하지 않을 때 던지는 도메인 예외 (HTTP 404).
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class ClassNotFoundException extends DomainException {

    public ClassNotFoundException() {
        super("CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Class not found");
    }

    public ClassNotFoundException(UUID id) {
        super("CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Class not found: " + id);
    }
}
