// 강의 상태 전이 중 낙관적 잠금 충돌이 재시도 후에도 해결되지 않을 때 던지는 도메인 예외 (HTTP 409).
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.shared.DomainException;
import org.springframework.http.HttpStatus;

public class ConcurrentClassUpdateException extends DomainException {

    public ConcurrentClassUpdateException() {
        super("CONCURRENT_CLASS_UPDATE", HttpStatus.CONFLICT,
                "Class was modified by another request. Please retry.");
    }
}
