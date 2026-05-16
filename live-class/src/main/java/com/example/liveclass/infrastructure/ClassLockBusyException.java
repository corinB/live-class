// classId 분산락 충돌로 임계 구간 진입이 거부됐을 때 던지는 예외 — 503 fail-closed 응답으로 매핑
package com.example.liveclass.infrastructure;

import java.util.UUID;

/**
 * apply / cancel / reconcile 사이에 같은 classId 락이 이미 다른 호출자에게 잡혀 있어 진입을 거부했을 때.
 * Web 계층 advice 가 503 Service Unavailable 로 매핑한다. 호출자는 짧은 backoff 후 재시도 권장.
 */
public class ClassLockBusyException extends RuntimeException {
    public ClassLockBusyException(UUID classId) {
        super("class lock busy for classId=" + classId);
    }
}
