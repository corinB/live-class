// 이미 취소된 수강신청에 대해 중복 취소 시도 시 발생하는 도메인 예외 — HTTP 409 Conflict 매핑
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class AlreadyCancelledException extends DomainException {

    public AlreadyCancelledException() {
        super("ALREADY_CANCELLED", HttpStatus.CONFLICT, "Enrollment is already cancelled");
    }
}
