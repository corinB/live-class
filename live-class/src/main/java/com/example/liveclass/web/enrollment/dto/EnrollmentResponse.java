// 수강신청 응답 DTO — Enrollment 엔티티를 직렬화 가능한 record 로 변환 (POST 응답 + my-enrollments 항목 공용)
package com.example.liveclass.web.enrollment.dto;

import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID id,
        UUID classId,
        UUID classmateId,
        EnrollmentStatus status,
        Instant appliedAt,
        Instant paidAt,
        Instant cancelledAt
) {
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getClassId(),
                enrollment.getClassmateId(),
                enrollment.getStatus(),
                enrollment.getAppliedAt(),
                enrollment.getPaidAt(),
                enrollment.getCancelledAt()
        );
    }
}
