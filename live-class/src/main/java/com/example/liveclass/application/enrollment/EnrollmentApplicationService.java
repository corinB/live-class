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
import com.example.liveclass.domain.enrollment.OutsideCancellationWindowException;
import com.example.liveclass.domain.enrollment.event.EnrollmentCancelledEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentConfirmedEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentCreatedEvent;
import com.example.liveclass.domain.enrollment.event.WaitlistPromotedEvent;
import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.infrastructure.ClassLockService;
import com.example.liveclass.web.enrollment.dto.EnrollmentResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final ClassLockService classLockService;
    private final TransactionTemplate applyTxTemplate;
    private final TransactionTemplate cancelTxTemplate;

    public EnrollmentApplicationService(ClassRepository classRepository,
                                        EnrollmentRepository enrollmentRepository,
                                        EnrollmentMirrorService mirrorService,
                                        ApplicationEventPublisher eventPublisher,
                                        MockPaymentGateway paymentGateway,
                                        ClassLockService classLockService,
                                        PlatformTransactionManager txManager) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.mirrorService = mirrorService;
        this.eventPublisher = eventPublisher;
        this.paymentGateway = paymentGateway;
        this.classLockService = classLockService;
        // apply 측 트랜잭션 — READ_COMMITTED 격리, 2초 타임아웃 (기존 @Transactional 설정 보존).
        this.applyTxTemplate = new TransactionTemplate(txManager);
        this.applyTxTemplate.setIsolationLevel(TransactionTemplate.ISOLATION_READ_COMMITTED);
        this.applyTxTemplate.setTimeout(2);
        // cancel 측 트랜잭션 — 기본 격리, 2초 타임아웃 (기존 @Transactional 설정 보존).
        this.cancelTxTemplate = new TransactionTemplate(txManager);
        this.cancelTxTemplate.setTimeout(2);
    }

    /**
     * 외부 진입점. classId 분산락(`lock:reconcile:{classId}`)으로 reconcile 와의 race 를 차단한 뒤,
     * 내부 TransactionTemplate 안에서 실제 apply 로직을 실행한다. 락 충돌 시
     * ClassLockBusyException 으로 503 매핑.
     */
    public EnrollmentResponse apply(UUID classmateId, UUID classId, Instant now) {
        return classLockService.executeWithLock(classId,
                () -> applyTxTemplate.execute(status -> applyInTx(classmateId, classId, now)));
    }

    private EnrollmentResponse applyInTx(UUID classmateId, UUID classId, Instant now) {
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
     * Confirms payment for a PENDING enrollment.
     * Mock payment charge is called at the start of this @Transactional method —
     * since mock payment has no external side effects, running it inside the TX
     * is an accepted tradeoff for implementation simplicity (ARCHITECTURE §6.2 intent met).
     * Uses @Version optimistic lock with one in-method retry on conflict.
     */
    @Transactional(timeout = 2)
    public EnrollmentResponse confirmPayment(UUID enrollmentId, UUID classmateId, Instant now) {
        // Mock payment — no real DB side effect, called before the state-mutating DB UPDATE
        paymentGateway.charge(enrollmentId);

        Enrollment e = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(EnrollmentNotFoundException::new);

        if (!e.getClassmateId().equals(classmateId)) {
            throw new AccessDeniedDomainException("You do not own this enrollment");
        }

        try {
            e.confirm(now);
            enrollmentRepository.save(e);
        } catch (OptimisticLockingFailureException ex) {
            // One retry: reload the fresh entity and re-attempt
            Enrollment retried = enrollmentRepository.findById(enrollmentId)
                    .orElseThrow(EnrollmentNotFoundException::new);
            if (!retried.getClassmateId().equals(classmateId)) {
                throw new AccessDeniedDomainException("You do not own this enrollment");
            }
            retried.confirm(now);
            enrollmentRepository.save(retried);
            e = retried;
        }

        eventPublisher.publishEvent(
                new EnrollmentConfirmedEvent(e.getId(), e.getClassId(), e.getClassmateId(), e.getPaidAt(), now));

        return EnrollmentResponse.from(e);
    }

    /**
     * Cancels an enrollment.
     * Idempotent: already-CANCELLED enrollment returns 200 without side effects.
     * 7-day window enforced for CONFIRMED status.
     * Waitlist promotion is done atomically via Lua cancel_promote script.
     *
     * 진입 시 enrollmentId 만 받으므로 classId 분산락을 잡으려면 먼저 enrollment row 를 읽어 classId 를
     * 얻어야 한다. 이를 위해 짧은 read-only 트랜잭션으로 classId 만 조회 후 본 트랜잭션 + 락을 잡는다.
     * 락 충돌 시 ClassLockBusyException 으로 503 매핑.
     */
    public EnrollmentResponse cancel(UUID enrollmentId, UUID classmateId, Instant now) {
        UUID classId = applyTxTemplate.execute(status -> enrollmentRepository.findById(enrollmentId)
                .map(Enrollment::getClassId)
                .orElseThrow(EnrollmentNotFoundException::new));

        return classLockService.executeWithLock(classId,
                () -> cancelTxTemplate.execute(status -> cancelInTx(enrollmentId, classmateId, now)));
    }

    private EnrollmentResponse cancelInTx(UUID enrollmentId, UUID classmateId, Instant now) {
        Enrollment e = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(EnrollmentNotFoundException::new);

        if (!e.getClassmateId().equals(classmateId)) {
            throw new AccessDeniedDomainException("You do not own this enrollment");
        }

        // Idempotent: already cancelled
        if (e.getStatus() == EnrollmentStatus.CANCELLED) {
            return EnrollmentResponse.from(e);
        }

        // 7-day window check for CONFIRMED
        if (e.getStatus() == EnrollmentStatus.CONFIRMED && !e.isWithinCancellationWindow(now)) {
            throw new OutsideCancellationWindowException();
        }

        EnrollmentStatus previousStatus = e.getStatus();
        boolean wasConfirmed = previousStatus == EnrollmentStatus.CONFIRMED;
        UUID classId = e.getClassId();

        try {
            e.cancel(now);
            enrollmentRepository.save(e);
        } catch (OptimisticLockingFailureException ex) {
            // Reload to distinguish: concurrent cancel (idempotent 200) vs genuine conflict (re-throw)
            Enrollment refreshed = enrollmentRepository.findById(enrollmentId)
                    .orElseThrow(EnrollmentNotFoundException::new);
            if (refreshed.getStatus() == EnrollmentStatus.CANCELLED) {
                return EnrollmentResponse.from(refreshed);
            }
            throw ex;
        }

        // Lua atomic ZREM + optional ZPOPMIN waitlist + ZADD enrolled
        List<String> luaResult = mirrorService.cancelAndMaybePromote(classId, classmateId, wasConfirmed);

        if (luaResult != null && luaResult.size() >= 2) {
            UUID promotedClassmateId = UUID.fromString(luaResult.get(0));
            long promotedScore = Long.parseLong(luaResult.get(1));

            Enrollment promoted = enrollmentRepository.findActiveByClassAndClassmate(classId, promotedClassmateId)
                    .orElseThrow(EnrollmentNotFoundException::new);

            try {
                promoted.promoteFromWaitlist(now);
                enrollmentRepository.save(promoted);
            } catch (Exception ex) {
                // DB UPDATE failed — reverse the Lua ZSET changes
                mirrorService.reverseCancelPromote(classId, classmateId, promotedClassmateId, promotedScore);
                throw ex;
            }

            eventPublisher.publishEvent(
                    new WaitlistPromotedEvent(promoted.getId(), classId, promotedClassmateId, now));
        }

        eventPublisher.publishEvent(
                new EnrollmentCancelledEvent(e.getId(), classId, classmateId, previousStatus, e.getCancelledAt(), now));

        return EnrollmentResponse.from(e);
    }

    private RuntimeException mapDbException(RuntimeException ex) {
        if (ex instanceof DataIntegrityViolationException) {
            return new DuplicateEnrollmentException();
        }
        return ex;
    }
}
