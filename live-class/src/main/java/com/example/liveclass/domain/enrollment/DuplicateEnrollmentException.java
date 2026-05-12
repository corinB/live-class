// 동일 강의에 중복 수강신청 시 발생하는 도메인 예외 — HTTP 409 Conflict 매핑
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class DuplicateEnrollmentException extends DomainException {

    public DuplicateEnrollmentException() {
        super("DUPLICATE_ENROLLMENT", HttpStatus.CONFLICT,
                "An active enrollment for this class already exists");
    }
}
