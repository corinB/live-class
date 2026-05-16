// EnrollmentEventListener 가 AFTER_COMMIT 시점에 4종 이벤트를 정확히 수신하는지 검증하는 통합 테스트.
package com.example.liveclass.application.enrollment;

import com.example.liveclass.domain.enrollment.EnrollmentStatus;
import com.example.liveclass.domain.enrollment.event.EnrollmentCancelledEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentConfirmedEvent;
import com.example.liveclass.domain.enrollment.event.EnrollmentCreatedEvent;
import com.example.liveclass.domain.enrollment.event.WaitlistPromotedEvent;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * AFTER_COMMIT listener 는 트랜잭션이 실제로 커밋된 뒤에만 호출돼야 한다.
 * publishEvent 단독 호출(트랜잭션 외부)에서는 호출되지 않고, TransactionTemplate 안에서
 * publishEvent 후 정상 커밋되면 각 listener 메서드가 정확히 1회 호출되는지 검증한다.
 */
@IntegrationTest
class EnrollmentEventListenerTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager txManager;

    @MockitoSpyBean
    private EnrollmentEventListener listener;

    @Test
    void afterCommit_invokesOnCreatedExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID enrollmentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID classmateId = UUID.randomUUID();
        Instant now = Instant.now();
        EnrollmentCreatedEvent event = new EnrollmentCreatedEvent(
                enrollmentId, classId, classmateId, EnrollmentStatus.PENDING, now);

        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(listener, times(1)).onCreated(event);
    }

    @Test
    void afterCommit_invokesOnConfirmedExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID enrollmentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID classmateId = UUID.randomUUID();
        Instant now = Instant.now();
        EnrollmentConfirmedEvent event = new EnrollmentConfirmedEvent(
                enrollmentId, classId, classmateId, now, now);

        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(listener, times(1)).onConfirmed(event);
    }

    @Test
    void afterCommit_invokesOnCancelledExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID enrollmentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID classmateId = UUID.randomUUID();
        Instant now = Instant.now();
        EnrollmentCancelledEvent event = new EnrollmentCancelledEvent(
                enrollmentId, classId, classmateId, EnrollmentStatus.CONFIRMED, now, now);

        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(listener, times(1)).onCancelled(event);
    }

    @Test
    void afterCommit_invokesOnWaitlistPromotedExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID enrollmentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        UUID classmateId = UUID.randomUUID();
        Instant now = Instant.now();
        WaitlistPromotedEvent event = new WaitlistPromotedEvent(
                enrollmentId, classId, classmateId, now);

        tx.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        verify(listener, times(1)).onWaitlistPromoted(event);
    }

    @Test
    void publishOutsideTransaction_listenerNotInvoked() {
        EnrollmentCreatedEvent event = new EnrollmentCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                EnrollmentStatus.PENDING, Instant.now());

        eventPublisher.publishEvent(event);

        verifyNoInteractions(listener);
    }

    @Test
    void rolledBackTransaction_listenerNotInvoked() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        EnrollmentCreatedEvent event = new EnrollmentCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                EnrollmentStatus.PENDING, Instant.now());

        tx.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        verifyNoInteractions(listener);
    }
}
