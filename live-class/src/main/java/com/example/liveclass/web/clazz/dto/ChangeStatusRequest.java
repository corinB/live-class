// 강의 상태 전이 요청 DTO — target 으로 OPEN 또는 CLOSED 만 허용한다.
package com.example.liveclass.web.clazz.dto;

import com.example.liveclass.domain.clazz.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "강의 상태 전이 요청. DRAFT→OPEN 또는 OPEN→CLOSED 전이만 의미가 있다.")
public record ChangeStatusRequest(
        @Schema(description = "전이할 목표 상태. OPEN 또는 CLOSED.",
                example = "OPEN", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull ClassStatus target
) {
}
