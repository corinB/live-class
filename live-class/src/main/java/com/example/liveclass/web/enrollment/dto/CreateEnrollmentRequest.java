// 수강신청 POST 요청 바디 DTO
package com.example.liveclass.web.enrollment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEnrollmentRequest(
        @NotNull(message = "classId must not be null")
        UUID classId
) {
}
