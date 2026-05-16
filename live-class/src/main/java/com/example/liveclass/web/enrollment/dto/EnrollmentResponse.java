// 수강신청 응답 DTO — Enrollment 엔티티를 직렬화 가능한 record 로 변환 (POST 응답 + my-enrollments 항목 공용)
package com.example.liveclass.web.enrollment.dto;

import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "수강신청 응답. 정원 여유 시 PENDING, 만원 시 WAITLISTED 로 응답된다.")
public record EnrollmentResponse(
        @Schema(description = "수강신청 ID.",
                example = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "신청한 강의 ID.",
                example = "11111111-2222-3333-4444-555555555555",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classId,

        @Schema(description = "신청자 사용자 ID.",
                example = "0b3a1c5e-7e2a-4a1f-9a3b-2e8c1d6f5a40",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classmateId,

        @Schema(description = "현재 상태. PENDING|CONFIRMED|CANCELLED|WAITLISTED.",
                example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
        EnrollmentStatus status,

        @Schema(description = "신청 시각 (waitlist FIFO 정렬 키, ZSET score).",
                example = "2026-05-16T03:14:15Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant appliedAt,

        @Schema(description = "결제 확정 시각. CONFIRMED 가 아니면 null. 취소 7일창 계산 기준.",
                example = "2026-05-16T03:20:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Instant paidAt,

        @Schema(description = "취소 시각. CANCELLED 가 아니면 null.",
                example = "2026-05-18T11:30:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
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
