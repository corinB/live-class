// 수강생 목록 페이지 응답 DTO — Spring Boot 4 PageImpl 직렬화 제한을 우회하는 명시적 wrapper
package com.example.liveclass.web.enrollment.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedStudentResponse(
        List<StudentResponse> content,
        int page,
        int size,
        long totalElements,
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
