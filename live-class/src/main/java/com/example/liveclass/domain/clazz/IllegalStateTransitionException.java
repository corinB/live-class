// 허용되지 않는 상태 전이 시도를 나타내는 도메인 예외 — HTTP 409 Conflict 매핑
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class IllegalStateTransitionException extends DomainException {

    public IllegalStateTransitionException(String message) {
        super("ILLEGAL_STATE_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
