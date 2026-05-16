// 사용자 응답 DTO.
package com.example.liveclass.web.user.dto;

import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "사용자 응답.")
public record UserResponse(
        @Schema(description = "사용자 ID (UUID).",
                example = "0b3a1c5e-7e2a-4a1f-9a3b-2e8c1d6f5a40",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "역할. 가입 후 불변.",
                example = "CLASSMATE", requiredMode = Schema.RequiredMode.REQUIRED)
        UserRole role,

        @Schema(description = "사용자 표시명.", example = "홍길동",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "가입 시각 (ISO-8601).",
                example = "2026-05-16T03:14:15Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getRole(), user.getName(), user.getCreatedAt());
    }
}
