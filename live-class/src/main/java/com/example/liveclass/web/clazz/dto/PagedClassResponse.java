// 강의 페이지 응답 DTO — Spring Boot 4 가 PageImpl 직접 직렬화를 거부하므로 명시적 wrapper 사용
package com.example.liveclass.web.clazz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "강의 목록 페이지 응답. Spring Data Page 의 명시적 직렬화 형태.")
public record PagedClassResponse(
        @Schema(description = "현재 페이지 항목.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<ClassResponse> content,

        @Schema(description = "현재 페이지 번호 (0-based).",
                example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,

        @Schema(description = "페이지 크기.", example = "20",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int size,

        @Schema(description = "전체 항목 수.", example = "57",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,

        @Schema(description = "전체 페이지 수.", example = "3",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages
) {
    public static PagedClassResponse from(Page<ClassResponse> page) {
        return new PagedClassResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
