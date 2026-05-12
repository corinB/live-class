// 취소 가능 기간(7일) 초과 시 발생하는 도메인 예외 — HTTP 422 Unprocessable Entity 매핑
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class OutsideCancellationWindowException extends DomainException {

    public OutsideCancellationWindowException() {
        super("OUTSIDE_CANCELLATION_WINDOW", HttpStatus.UNPROCESSABLE_ENTITY,
                "Cancellation is not allowed after the 7-day cancellation window");
    }
}
