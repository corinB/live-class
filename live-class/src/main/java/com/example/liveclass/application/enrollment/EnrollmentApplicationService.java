// 수강신청 Lua-first 흐름을 오케스트레이션하는 application service — 정원 검사·DB INSERT·보상·이벤트 발행 담당
package com.example.liveclass.application.enrollment;

import com.example.liveclass.application.payment.MockPaymentGateway;
import com.example.liveclass.domain.clazz.AccessDeniedDomainException;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.clazz.ClassNotFoundException;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.enrollment.ClassNotOpenException;
import com.example.liveclass.domain.enrollment.DuplicateEnrollmentException;
import com.example.liveclass.domain.enrollment.Enrollment;
import com.example.liveclass.domain.enrollment.EnrollmentNotFoundException;
import com.example.liveclass.domain.enrollment.EnrollmentRepository;
import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.enrollment.event.EnrollmentCancelledEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentConfirmedEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentCreatedEvent;
import com.example.liveclass.domain.enrollment.event.WaitlistPromotedEvent;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EnrollmentApplicationService {

    private final ClassRepository classRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentMirrorService mirrorService;
    private final ApplicationEventPublisher eventPublisher;
    private final MockPaymentGateway paymentGateway;

    // Self-reference via @Lazy to allow @Transactional proxy on confirmPaymentTx
    @Autowired
    @org.springframework.context.annotation.Lazy
    private EnrollmentApplicationService self;

    public EnrollmentApplicationService(ClassRepository classRepository,
                                        EnrollmentRepository enrollmentRepository,
                                        EnrollmentMirrorService mirrorService,
                                        ApplicationEventPublisher eventPublisher,
                                        MockPaymentGateway paymentGateway) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.mirrorService = mirrorService;
        this.eventPublisher = eventPublisher;
        this.paymentGateway = paymentGateway;
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
        } catch (DataAccessException ex) {
            // Narrowed to Spring's DataAccessException hierarchy: covers
            // DataIntegrityViolationException, OptimisticLockingFailureException,
            // JpaSystemException etc. Crucially does NOT catch MirrorUnavailableException
            // (a plain RuntimeException), so compensation Lua failure won't recurse.
            mirrorService.compensateApply(classId, classmateId);
            throw mapDbException(ex);
        }

        eventPublisher.publishEvent(
                new EnrollmentCreatedEvent(enrollment.getId(), classId, classmateId, enrollment.getStatus(), now));

        return EnrollmentResponse.from(enrollment);
    }

    /**
     * Confirms payment for an enrollment.
     * MockPaymentGateway.charge() is called before the transaction starts per ARCHITECTURE §6.2.
     * On optimistic lock failure, retries once via self-proxy to open a fresh transaction.
     */
    public EnrollmentResponse confirmPayment(UUID enrollmentId, UUID classmateId, Instant now) {
        // charge() is outside the transaction per ARCHITECTURE §4.4
        paymentGateway.charge(enrollmentId);

        try {
            return self.confirmPaymentTx(enrollmentId, classmateId, now);
        } catch (OptimisticLockingFailureException ex) {
            // Retry once in a new transaction — ARCHITECTURE §6.2
            return self.confirmPaymentTx(enrollmentId, classmateId, now);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 2)
    public EnrollmentResponse confirmPaymentTx(UUID enrollmentId, UUID classmateId, Instant now) {
        Enrollment e = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(EnrollmentNotFoundException::new);

        if (!e.getClassmateId().equals(classmateId)) {
            throw new AccessDeniedDomainException("Enrollment does not belong to the requesting classmate");
        }

        e.confirm(now);
        enrollmentRepository.save(e);

        // No ZSET update — enrolled ZSET covers both PENDING and CONFIRMED per ARCHITECTURE §6.2
        eventPublisher.publishEvent(
                new EnrollmentConfirmedEvent(e.getId(), e.getClassId(), classmateId, e.getPaidAt(), now));

        return EnrollmentResponse.from(e);
    }

    @Transactional(timeout = 2)
    public EnrollmentResponse cancel(UUID enrollmentId, UUID classmateId, Instant now) {
        Enrollment e = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(EnrollmentNotFoundException::new);

        if (!e.getClassmateId().equals(classmateId)) {
            throw new AccessDeniedDomainException("Enrollment does not belong to the requesting classmate");
        }

        // Idempotent: already cancelled → return 200
        if (e.getStatus() == EnrollmentStatus.CANCELLED) {
            return EnrollmentResponse.from(e);
        }

        EnrollmentStatus prev = e.getStatus();

        try {
            e.cancel(now);
            enrollmentRepository.save(e);
        } catch (OptimisticLockingFailureException ex) {
            // Concurrent cancel already succeeded — idempotent 200
            Enrollment reloaded = enrollmentRepository.findById(enrollmentId)
                    .orElseThrow(EnrollmentNotFoundException::new);
            return EnrollmentResponse.from(reloaded);
        }

        // Atomic ZSET swap: remove cancelled, optionally promote oldest waitlisted
        List<String> luaResult = mirrorService.cancelAndMaybePromote(
                e.getClassId(), classmateId, prev == EnrollmentStatus.CONFIRMED);

        if (luaResult != null && !luaResult.isEmpty()) {
            UUID promotedClassmateId = UUID.fromString(luaResult.get(0));
            long promotedScore = Double.valueOf(luaResult.get(1)).longValue();

            Enrollment promoted = enrollmentRepository.findActiveByClassAndClassmate(
                            e.getClassId(), promotedClassmateId)
                    .orElseThrow(EnrollmentNotFoundException::new);
            try {
                promoted.promoteFromWaitlist(now);
                enrollmentRepository.save(promoted);
            } catch (Exception ex) {
                mirrorService.reverseCancelPromote(
                        e.getClassId(), classmateId, promotedClassmateId, promotedScore);
                throw ex;
            }

            eventPublisher.publishEvent(
                    new WaitlistPromotedEvent(promoted.getId(), e.getClassId(), promotedClassmateId, now));
        }

        eventPublisher.publishEvent(
                new EnrollmentCancelledEvent(
                        e.getId(), e.getClassId(), classmateId, prev, e.getCancelledAt(), now));

        return EnrollmentResponse.from(e);
    }

    private RuntimeException mapDbException(RuntimeException ex) {
        if (ex instanceof DataIntegrityViolationException) {
            return new DuplicateEnrollmentException();
        }
        return ex;
    }
}
