// 강의 응답 DTO — Class Aggregate 의 외부 표현.
package com.example.liveclass.web.clazz.dto;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "강의 응답. Class Aggregate 의 외부 표현.")
public record ClassResponse(
        @Schema(description = "강의 ID (UUID).",
                example = "11111111-2222-3333-4444-555555555555",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,

        @Schema(description = "강의 제목. 1~200자.", example = "Spring Boot 4 라이브 강의",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "강의 설명. 최대 2000자.",
                example = "8주 동안 실전 프로젝트 중심으로 진행합니다.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String description,

        @Schema(description = "수강료 금액. 0 이상.", example = "150000",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal priceAmount,

        @Schema(description = "수강료 통화 (ISO 4217 3-letter).", example = "KRW",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String priceCurrency,

        @Schema(description = "정원. 1 이상. DRAFT 에서만 변경 가능.",
                example = "30", requiredMode = Schema.RequiredMode.REQUIRED)
        int capacity,

        @Schema(description = "강의 시작일.", example = "2026-06-01",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate startDate,

        @Schema(description = "강의 종료일. startDate 이상.", example = "2026-07-26",
                requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate endDate,

        @Schema(description = "현재 상태. 일방향 전이 DRAFT→OPEN→CLOSED.",
                example = "OPEN", requiredMode = Schema.RequiredMode.REQUIRED)
        ClassStatus status,

        @Schema(description = "강의 개설자 사용자 ID.",
                example = "0b3a1c5e-7e2a-4a1f-9a3b-2e8c1d6f5a40",
                requiredMode = Schema.RequiredMode.REQUIRED)
        UUID creatorId,

        @Schema(description = "생성 시각 (ISO-8601).",
                example = "2026-05-16T03:14:15Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "마지막 수정 시각 (ISO-8601).",
                example = "2026-05-17T09:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt,

        @Schema(description = "JPA 낙관적 락 버전. 상태 전이마다 증가.",
                example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long version
) {
    public static ClassResponse from(Class clazz) {
        return new ClassResponse(
                clazz.getId(),
                clazz.getTitle(),
                clazz.getDescription(),
                clazz.getPrice().getAmount(),
                clazz.getPrice().getCurrency(),
                clazz.getCapacity().getValue(),
                clazz.getPeriod().getStartDate(),
                clazz.getPeriod().getEndDate(),
                clazz.getStatus(),
                clazz.getCreatorId(),
                clazz.getCreatedAt(),
                clazz.getUpdatedAt(),
                clazz.getVersion()
        );
    }
}
