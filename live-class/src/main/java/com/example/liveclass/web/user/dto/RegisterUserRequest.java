// 사용자 등록 요청 DTO.
package com.example.liveclass.web.user.dto;

import com.example.liveclass.domain.user.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "사용자 등록 요청. CREATOR 또는 CLASSMATE 역할 중 하나로 가입한다.")
public record RegisterUserRequest(
        @Schema(description = "사용자 표시명. 1~50자.", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "역할. CREATOR 는 강의 개설, CLASSMATE 는 수강 신청.",
                example = "CLASSMATE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull UserRole role
) {
}
