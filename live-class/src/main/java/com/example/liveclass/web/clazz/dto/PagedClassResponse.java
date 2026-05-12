// 강의 페이지 응답 DTO — Spring Boot 4 가 PageImpl 직접 직렬화를 거부하므로 명시적 wrapper 사용
package com.example.liveclass.web.clazz.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedClassResponse(
        List<ClassResponse> content,
        int page,
        int size,
        long totalElements,
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
