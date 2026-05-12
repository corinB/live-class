// 사용자 응답 DTO.
package com.example.liveclass.web.user.dto;

import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UserRole role,
        String name,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getRole(), user.getName(), user.getCreatedAt());
    }
}
