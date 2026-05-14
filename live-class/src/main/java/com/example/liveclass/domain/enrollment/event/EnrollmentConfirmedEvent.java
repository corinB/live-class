// 수강신청 결제 확정 시 발행되는 도메인 이벤트 — AFTER_COMMIT 리스너에서 소비
package com.example.liveclass.domain.enrollment.event;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentConfirmedEvent(
        UUID enrollmentId,
        UUID classId,
        UUID classmateId,
        Instant paidAt,
        Instant occurredAt
) {
}
