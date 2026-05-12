// 사용자 등록 요청 DTO.
package com.example.liveclass.web.user.dto;

import com.example.liveclass.domain.user.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterUserRequest(
        @NotBlank String name,
        @NotNull UserRole role
) {
}
