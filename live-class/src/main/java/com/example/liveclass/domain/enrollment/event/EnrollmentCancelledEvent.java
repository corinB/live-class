// 수강신청 취소 시 발행되는 도메인 이벤트 — AFTER_COMMIT 리스너에서 소비
package com.example.liveclass.domain.enrollment.event;

import com.example.liveclass.domain.enrollment.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentCancelledEvent(
        UUID enrollmentId,
        UUID classId,
        UUID classmateId,
        EnrollmentStatus previousStatus,
        Instant cancelledAt,
        Instant occurredAt
) {
}
