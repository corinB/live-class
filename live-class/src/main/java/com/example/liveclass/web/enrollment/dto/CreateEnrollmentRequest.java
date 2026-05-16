// 수강신청 POST 요청 바디 DTO
package com.example.liveclass.web.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "수강신청 요청. 호출자(`X-User-Id`)가 CLASSMATE 여야 한다.")
public record CreateEnrollmentRequest(
        @Schema(description = "신청 대상 강의 ID. 강의 상태가 OPEN 이어야 한다.",
                example = "11111111-2222-3333-4444-555555555555",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "classId must not be null")
        UUID classId
) {
}
