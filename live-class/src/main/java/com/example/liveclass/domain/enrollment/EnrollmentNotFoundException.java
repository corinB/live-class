// 수강 신청을 찾을 수 없을 때 발생하는 도메인 예외 — HTTP 404 Not Found 매핑
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class EnrollmentNotFoundException extends DomainException {

    public EnrollmentNotFoundException() {
        super("ENROLLMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Enrollment not found");
    }
}
