// 수강신청 읽기 전용 쿼리 서비스 — Creator 수강생 목록과 Classmate 본인 수강 내역 조회를 담당한다
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassNotFoundException;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.user.User;
import com.example.liveclass.domain.user.UserRepository;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import com.example.liveclass.web.enrollment.dto.StudentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EnrollmentQueryService {

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    public EnrollmentQueryService(EnrollmentRepository enrollmentRepository,
                                  ClassRepository classRepository,
                                  UserRepository userRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.classRepository = classRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns a page of confirmed students for the given class.
     * Only the class creator is allowed; throws 403 otherwise.
     */
    public Page<StudentResponse> listStudents(UUID classId, UUID requesterId, Pageable pageable) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(ClassNotFoundException::new);

        if (!clazz.getCreatorId().equals(requesterId)) {
            throw new AccessDeniedDomainException(
                    "Only the class creator can view the student list");
        }

        Page<Enrollment> enrollmentPage = enrollmentRepository.findConfirmedByClassId(classId, pageable);

        // Batch-load all classmate users in one query to avoid N+1.
        List<UUID> classmateIds = enrollmentPage.getContent().stream()
                .map(Enrollment::getClassmateId)
                .distinct()
                .collect(Collectors.toList());

        Map<UUID, String> nameById = userRepository.findAllByIdIn(classmateIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));

        List<StudentResponse> responses = enrollmentPage.getContent().stream()
                .map(e -> StudentResponse.of(e, nameById.getOrDefault(e.getClassmateId(), "")))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, enrollmentPage.getTotalElements());
    }

    /**
     * Returns a page of the caller's own enrollments, optionally filtered by status.
     * When statuses is empty, all statuses are returned.
     */
    public Page<EnrollmentResponse> listMyEnrollments(UUID classmateId,
                                                      Set<EnrollmentStatus> statuses,
                                                      Pageable pageable) {
        Page<Enrollment> page;
        if (statuses == null || statuses.isEmpty()) {
            page = enrollmentRepository.findByClassmateIdOrderByAppliedAtDesc(classmateId, pageable);
        } else {
            page = enrollmentRepository.findByClassmateIdAndStatusInOrderByAppliedAtDesc(
                    classmateId, statuses, pageable);
        }
        return page.map(EnrollmentResponse::from);
    }
}
