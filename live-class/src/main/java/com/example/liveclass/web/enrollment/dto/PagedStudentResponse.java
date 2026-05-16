// 수강생 목록 페이지 응답 DTO — Spring Boot 4 PageImpl 직렬화 제한을 우회하는 명시적 wrapper
package com.example.liveclass.web.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Creator 전용 수강생 목록 페이지 응답.")
public record PagedStudentResponse(
        @Schema(description = "현재 페이지 항목.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<StudentResponse> content,

        @Schema(description = "현재 페이지 번호 (0-based).",
                example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,

        @Schema(description = "페이지 크기.", example = "20",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int size,

        @Schema(description = "전체 항목 수.", example = "30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,

        @Schema(description = "전체 페이지 수.", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages
) {
    public static PagedStudentResponse from(Page<StudentResponse> page) {
        return new PagedStudentResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
