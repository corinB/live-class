// 수강신청 REST 컨트롤러 — POST / (신청), GET /me (본인 수강 내역 조회)
package com.example.liveclass.web.enrollment;

import com.example.liveclass.application.enrollment.EnrollmentApplicationService;
import com.example.liveclass.application.enrollment.EnrollmentQueryService;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.web.auth.CurrentUserId;
import com.example.liveclass.web.enrollment.dto.CreateEnrollmentRequest;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import com.example.liveclass.web.enrollment.dto.PagedEnrollmentResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentApplicationService enrollmentApplicationService;
    private final EnrollmentQueryService enrollmentQueryService;

    public EnrollmentController(EnrollmentApplicationService enrollmentApplicationService,
                                 EnrollmentQueryService enrollmentQueryService) {
        this.enrollmentApplicationService = enrollmentApplicationService;
        this.enrollmentQueryService = enrollmentQueryService;
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> apply(@CurrentUserId UUID classmateId,
                                                     @Valid @RequestBody CreateEnrollmentRequest req) {
        EnrollmentResponse response = enrollmentApplicationService.apply(classmateId, req.classId(), Instant.now());
        int httpStatus = response.status() == EnrollmentStatus.PENDING ? 201 : 202;
        return ResponseEntity.status(httpStatus).body(response);
    }

    @GetMapping("/me")
    public PagedEnrollmentResponse myEnrollments(
            @CurrentUserId UUID classmateId,
            @RequestParam(name = "status", required = false) String statusParam,
            @PageableDefault(size = 20) Pageable pageable) {
        Set<EnrollmentStatus> statuses = parseStatuses(statusParam);
        return PagedEnrollmentResponse.from(
                enrollmentQueryService.listMyEnrollments(classmateId, statuses, pageable));
    }

    private Set<EnrollmentStatus> parseStatuses(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(statusParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(EnrollmentStatus::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
