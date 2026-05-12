// 수강신청 생성 시 발행되는 도메인 이벤트 — AFTER_COMMIT 리스너에서 소비
package com.example.liveclass.domain.enrollment.event;

import com.example.liveclass.domain.enrollment.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentCreatedEvent(
        UUID enrollmentId,
        UUID classId,
        UUID classmateId,
        EnrollmentStatus status,
        Instant occurredAt
) {
}
