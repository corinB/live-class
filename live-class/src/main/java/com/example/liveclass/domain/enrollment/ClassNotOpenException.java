// 수강신청 불가 상태의 강의에 신청 시 발생하는 도메인 예외 — HTTP 409 Conflict 매핑
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class ClassNotOpenException extends DomainException {

    public ClassNotOpenException() {
        super("CLASS_NOT_OPEN", HttpStatus.CONFLICT, "The class is not open for enrollment");
    }
}
