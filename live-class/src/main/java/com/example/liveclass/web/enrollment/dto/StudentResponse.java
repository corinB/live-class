// Creator 전용 수강생 목록 응답 DTO — Enrollment 한 건의 수강생 정보를 담는다
package com.example.liveclass.web.enrollment.dto;

import com.example.liveclass.domain.enrollment.Enrollment;

import java.time.Instant;
import java.util.UUID;

public record StudentResponse(
        UUID enrollmentId,
        UUID classmateId,
        String classmateName,
        Instant paidAt
) {
    public static StudentResponse of(Enrollment enrollment, String classmateName) {
        return new StudentResponse(
                enrollment.getId(),
                enrollment.getClassmateId(),
                classmateName,
                enrollment.getPaidAt()
        );
    }
}
