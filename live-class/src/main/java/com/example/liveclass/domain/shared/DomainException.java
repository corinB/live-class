// 모든 도메인 예외의 추상 베이스 — errorCode + HTTP status 를 캡슐화
package com.example.liveclass.domain.shared;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class DomainException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected DomainException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
