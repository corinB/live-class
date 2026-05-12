// 수강신청 단건 응답 DTO — my-enrollments 목록의 각 항목을 표현한다
package com.example.liveclass.web.enrollment.dto;

import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;

import java.time.Instant;
import java.util.UUID;

public record EnrollmentResponse(
        UUID enrollmentId,
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
