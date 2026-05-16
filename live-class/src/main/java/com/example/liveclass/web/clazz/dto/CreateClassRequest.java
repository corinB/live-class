// 강의(Class) 초안 생성 요청 DTO — Bean Validation 으로 입력값을 검증한다.
package com.example.liveclass.web.clazz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "강의 초안 생성 요청. 호출자가 CREATOR 역할이어야 한다. 생성 직후 상태는 DRAFT.")
public record CreateClassRequest(
        @Schema(description = "강의 제목. 1~200자.",
                example = "Spring Boot 4 라이브 강의",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 200) String title,

        @Schema(description = "강의 설명. 최대 2000자.",
                example = "8주 동안 실전 프로젝트 중심으로 진행합니다.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 2000) String description,

        @Schema(description = "수강료 금액. 0 이상의 소수.",
                example = "150000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero BigDecimal priceAmount,

        @Schema(description = "통화 코드 (ISO 4217, 3-letter).",
                example = "KRW", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(min = 3, max = 3) String priceCurrency,

        @Schema(description = "정원. 1 이상.", example = "30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(1) Integer capacity,

        @Schema(description = "강의 시작일. 오늘 이후 또는 오늘.",
                example = "2026-06-01", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @FutureOrPresent LocalDate startDate,

        @Schema(description = "강의 종료일. startDate 이상.",
                example = "2026-07-26", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate endDate
) {
}
