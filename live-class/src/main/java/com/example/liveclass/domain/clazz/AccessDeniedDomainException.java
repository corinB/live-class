// 강의 상태 전이 권한 없음을 나타내는 도메인 예외 — HTTP 403 Forbidden 매핑
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class AccessDeniedDomainException extends DomainException {

    public AccessDeniedDomainException(String message) {
        super("ACCESS_DENIED", HttpStatus.FORBIDDEN, message);
    }
}
