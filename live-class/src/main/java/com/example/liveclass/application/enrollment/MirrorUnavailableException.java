// Redis 연결 실패 시 EnrollmentMirrorService 가 변환해서 throw 하는 예외 — GlobalExceptionHandler 가 503 으로 매핑
package com.example.liveclass.application.enrollment;

public class MirrorUnavailableException extends RuntimeException {

    public MirrorUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
