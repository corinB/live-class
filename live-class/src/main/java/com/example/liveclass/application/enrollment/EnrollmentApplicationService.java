// 수강신청 Lua-first 흐름을 오케스트레이션하는 application service — 정원 검사·DB INSERT·보상·이벤트 발행 담당
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassNotFoundException;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.enrollment.ClassNotOpenException;
import com.example.liveclass.domain.enrollment.DuplicateEnrollmentException;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.event.EnrollmentCreatedEvent;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EnrollmentApplicationService {

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMirrorService mirrorService;
    private final ApplicationEventPublisher eventPublisher;

    public EnrollmentApplicationService(ClassRepository classRepository,
                                        EnrollmentRepository enrollmentRepository,
                                        EnrollmentMirrorService mirrorService,
                                        ApplicationEventPublisher eventPublisher) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.mirrorService = mirrorService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 2)
    public EnrollmentResponse apply(UUID classmateId, UUID classId, Instant now) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(ClassNotFoundException::new);

        if (clazz.getCreatorId().equals(classmateId)) {
            throw new CreatorCannotEnrollException();
        }

        long appliedAtNanos = now.getEpochSecond() * 1_000_000_000L + now.getNano();

        String luaResult = mirrorService.tryApply(classId, classmateId, appliedAtNanos, clazz.getCapacity().getValue());

        if ("CLASS_NOT_FOUND".equals(luaResult)) {
            mirrorService.primeClassStatusMirror(classId, clazz.getStatus());
            luaResult = mirrorService.tryApply(classId, classmateId, appliedAtNanos, clazz.getCapacity().getValue());
        }

        switch (luaResult) {
            case "DUPLICATE_ACTIVE" -> throw new DuplicateEnrollmentException();
            case "CLASS_NOT_OPEN" -> throw new ClassNotOpenException();
            case "CLASS_NOT_FOUND" -> throw new ClassNotOpenException();
            default -> { /* PENDING or WAITLISTED — continue */ }
        }

        Enrollment enrollment;
        if ("PENDING".equals(luaResult)) {
            enrollment = Enrollment.apply(ClassId.of(classId), UserId.of(classmateId), now);
        } else {
            enrollment = Enrollment.waitlist(ClassId.of(classId), UserId.of(classmateId), now);
        }

        try {
            enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException | RuntimeException ex) {
            mirrorService.compensateApply(classId, classmateId);
            throw mapDbException(ex);
        }

        eventPublisher.publishEvent(
                new EnrollmentCreatedEvent(enrollment.getId(), classId, classmateId, enrollment.getStatus(), now));

        return EnrollmentResponse.from(enrollment);
    }

    private RuntimeException mapDbException(RuntimeException ex) {
        if (ex instanceof DataIntegrityViolationException) {
            return new DuplicateEnrollmentException();
        }
        return ex;
    }
}
