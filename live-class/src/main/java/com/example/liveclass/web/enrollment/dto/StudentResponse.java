// Creator 전용 수강생 목록 응답 DTO — Enrollment 한 건의 수강생 정보를 담는다
package com.example.liveclass.web.enrollment.dto;

import com.example.liveclass.domain.enrollment.Enrollment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Creator 가 자기 강의 수강생을 조회할 때의 항목 응답.")
public record StudentResponse(
        @Schema(description = "수강신청 ID.",
                example = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID enrollmentId,

        @Schema(description = "수강생 사용자 ID.",
                example = "0b3a1c5e-7e2a-4a1f-9a3b-2e8c1d6f5a40",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID classmateId,

        @Schema(description = "수강생 표시명.", example = "홍길동",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String classmateName,

        @Schema(description = "결제 확정 시각. CONFIRMED 가 아니면 null.",
                example = "2026-05-16T03:20:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Instant paidAt
) {
    public static StudentResponse of(Enrollment enrollment, String classmateName) {
        return new StudentResponse(
                enrollment.getId(),
                enrollment.getClassmateId(),
                classmateName,
                enrollment.getPaidAt()
        );
    }
}
