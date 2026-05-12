// 수강신청 REST 컨트롤러 — POST /api/enrollments (PENDING 201, WAITLISTED 202)
package com.example.liveclass.web.enrollment;

import com.example.liveclass.application.enrollment.EnrollmentApplicationService;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.web.auth.CurrentUserId;
import com.example.liveclass.web.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentApplicationService enrollmentApplicationService;

    public EnrollmentController(EnrollmentApplicationService enrollmentApplicationService) {
        this.enrollmentApplicationService = enrollmentApplicationService;
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> apply(@CurrentUserId UUID classmateId,
                                                     @Valid @RequestBody CreateEnrollmentRequest req) {
        EnrollmentResponse response = enrollmentApplicationService.apply(classmateId, req.classId(), Instant.now());
        int httpStatus = response.status() == EnrollmentStatus.PENDING ? 201 : 202;
        return ResponseEntity.status(httpStatus).body(response);
    }
}
