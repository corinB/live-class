// 강의 생성·상태 전이·조회 REST 컨트롤러.
package com.example.liveclass.web.clazz;

import com.example.liveclass.application.clazz.ClassApplicationService;
import com.example.liveclass.application.enrollment.EnrollmentQueryService;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.web.auth.CurrentUserId;
import com.example.liveclass.web.clazz.dto.ChangeStatusRequest;
import com.example.liveclass.web.clazz.dto.ClassResponse;
import com.example.liveclass.web.clazz.dto.CreateClassRequest;
import com.example.liveclass.web.clazz.dto.PagedClassResponse;
import com.example.liveclass.web.enrollment.dto.PagedStudentResponse;
import com.example.liveclass.web.enrollment.dto.StudentResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/classes")
public class ClassController {

    private final ClassApplicationService classApplicationService;
    private final EnrollmentQueryService enrollmentQueryService;

    public ClassController(ClassApplicationService classApplicationService,
                           EnrollmentQueryService enrollmentQueryService) {
        this.classApplicationService = classApplicationService;
        this.enrollmentQueryService = enrollmentQueryService;
    }

    @PostMapping
    public ResponseEntity<ClassResponse> create(@CurrentUserId UUID creatorId,
                                                @Valid @RequestBody CreateClassRequest req) {
        Class clazz = classApplicationService.createDraft(creatorId, req);
        return ResponseEntity.status(201).body(ClassResponse.from(clazz));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ClassResponse> changeStatus(@CurrentUserId UUID requesterId,
                                                      @PathVariable("id") UUID id,
                                                      @Valid @RequestBody ChangeStatusRequest req) {
        Class clazz = classApplicationService.transitionStatus(id, requesterId, req.target());
        return ResponseEntity.ok(ClassResponse.from(clazz));
    }

    @GetMapping("/{id}")
    public ClassResponse getOne(@PathVariable("id") UUID id) {
        return ClassResponse.from(classApplicationService.getById(id));
    }

    @GetMapping
    public PagedClassResponse listOpen(Pageable pageable) {
        // Spring Boot 4 가 PageImpl 직접 직렬화를 거부하므로 명시적 DTO wrapper 로 반환.
        Page<ClassResponse> page = classApplicationService.listOpenClasses(pageable).map(ClassResponse::from);
        return PagedClassResponse.from(page);
    }

    @GetMapping("/{id}/students")
    public PagedStudentResponse listStudents(
            @CurrentUserId UUID requesterId,
            @PathVariable("id") UUID classId,
            @PageableDefault(size = 20, sort = "paidAt") Pageable pageable) {
        Page<StudentResponse> page = enrollmentQueryService.listStudents(classId, requesterId, pageable);
        return PagedStudentResponse.from(page);
    }
}
