// 대기열 승격 시 발행되는 도메인 이벤트 — AFTER_COMMIT 리스너에서 소비
package com.example.liveclass.domain.enrollment.event;

import java.time.Instant;
import java.util.UUID;

public record WaitlistPromotedEvent(
        UUID enrollmentId,
        UUID classId,
        UUID classmateId,
        Instant occurredAt
) {
}
