// Creator 가 본인 강의에 수강신청 시 발생하는 예외 — HTTP 403 Forbidden
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class CreatorCannotEnrollException extends DomainException {

    public CreatorCannotEnrollException() {
        super("CREATOR_CANNOT_ENROLL", HttpStatus.FORBIDDEN,
                "The creator of a class cannot enroll in their own class");
    }
}
