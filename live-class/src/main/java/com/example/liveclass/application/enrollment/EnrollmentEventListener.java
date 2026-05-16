// 수강신청 도메인 이벤트 4종을 AFTER_COMMIT 단계에서 수신하는 트랜잭션 이벤트 리스너.
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.enrollment.event.EnrollmentCancelledEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentConfirmedEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentCreatedEvent;
import com.example.liveclass.domain.enrollment.event.WaitlistPromotedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Enrollment 도메인 이벤트(생성·확정·취소·대기열 승격) 의 AFTER_COMMIT 수신 지점.
 *
 * DOCS.md §49-62 — 이벤트는 트랜잭션 커밋 후 처리되어야 한다. 본 리스너는
 * 향후 알림 발송 / 메트릭 / 외부 큐 발행 등의 hook 자리를 명시적으로 마련한다.
 * 현 단계에서는 수신 사실만 INFO 로그로 기록 (실제 hook 로직은 별도 사이클에서 도입).
 */
@Slf4j
@Component
public class EnrollmentEventListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(EnrollmentCreatedEvent event) {
        log.info("enrollment created — enrollmentId={}, classId={}, classmateId={}, status={}",
                event.enrollmentId(), event.classId(), event.classmateId(), event.status());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(EnrollmentConfirmedEvent event) {
        log.info("enrollment confirmed — enrollmentId={}, classId={}, classmateId={}, paidAt={}",
                event.enrollmentId(), event.classId(), event.classmateId(), event.paidAt());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(EnrollmentCancelledEvent event) {
        log.info("enrollment cancelled — enrollmentId={}, classId={}, classmateId={}, previousStatus={}",
                event.enrollmentId(), event.classId(), event.classmateId(), event.previousStatus());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWaitlistPromoted(WaitlistPromotedEvent event) {
        log.info("waitlist promoted — enrollmentId={}, classId={}, classmateId={}",
                event.enrollmentId(), event.classId(), event.classmateId());
    }
}
