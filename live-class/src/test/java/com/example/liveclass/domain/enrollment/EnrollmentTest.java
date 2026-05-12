// 수강신청 Aggregate Root의 상태전이 시나리오 10개를 검증하는 순수 단위 테스트
package com.example.liveclass.domain.enrollment;

import com.example.liveclass.domain.clazz.ClassId;
import com.example.liveclass.domain.user.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    private static final ClassId CLASS_ID = ClassId.newId();
    private static final UserId USER_ID = UserId.newId();

    @Test
    void apply_creates_enrollment_with_PENDING_status_and_null_paidAt() {
        Instant now = Instant.now();
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, now);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getPaidAt()).isNull();
        assertThat(enrollment.getAppliedAt()).isEqualTo(now);
    }

    @Test
    void waitlist_creates_enrollment_with_WAITLISTED_status() {
        Instant now = Instant.now();
        Enrollment enrollment = Enrollment.waitlist(CLASS_ID, USER_ID, now);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void confirm_transitions_PENDING_to_CONFIRMED_and_sets_paidAt() {
        Instant appliedAt = Instant.now();
        Instant confirmedAt = appliedAt.plusSeconds(60);
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, appliedAt);

        enrollment.confirm(confirmedAt);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
        assertThat(enrollment.getPaidAt()).isEqualTo(confirmedAt);
    }

    @Test
    void cancel_PENDING_enrollment_transitions_to_CANCELLED() {
        Instant now = Instant.now();
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, now);

        enrollment.cancel(now);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(enrollment.getCancelledAt()).isEqualTo(now);
    }

    @Test
    void cancel_WAITLISTED_enrollment_transitions_to_CANCELLED() {
        Instant now = Instant.now();
        Enrollment enrollment = Enrollment.waitlist(CLASS_ID, USER_ID, now);

        enrollment.cancel(now);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void cancel_CONFIRMED_enrollment_within_6_days_succeeds() {
        Instant t0 = Instant.now();
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, t0);
        enrollment.confirm(t0);

        Instant cancelAt = t0.plus(6, ChronoUnit.DAYS);
        enrollment.cancel(cancelAt);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void cancel_CONFIRMED_enrollment_at_exactly_7_days_boundary_succeeds() {
        Instant t0 = Instant.now();
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, t0);
        enrollment.confirm(t0);

        Instant cancelAt = t0.plus(7, ChronoUnit.DAYS);
        enrollment.cancel(cancelAt);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void cancel_CONFIRMED_enrollment_at_7days_plus_1ns_throws_OutsideCancellationWindowException() {
        Instant t0 = Instant.now();
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, t0);
        enrollment.confirm(t0);

        Instant cancelAt = t0.plus(7, ChronoUnit.DAYS).plusNanos(1);

        assertThatThrownBy(() -> enrollment.cancel(cancelAt))
                .isInstanceOf(OutsideCancellationWindowException.class);
    }

    @Test
    void cancel_already_CANCELLED_enrollment_throws_AlreadyCancelledException() {
        Instant now = Instant.now();
        Enrollment enrollment = Enrollment.apply(CLASS_ID, USER_ID, now);
        enrollment.cancel(now);

        assertThatThrownBy(() -> enrollment.cancel(now))
                .isInstanceOf(AlreadyCancelledException.class);
    }

    @Test
    void confirm_WAITLISTED_enrollment_throws_IllegalStateTransitionException() {
        Instant now = Instant.now();
        Enrollment enrollment = Enrollment.waitlist(CLASS_ID, USER_ID, now);

        assertThatThrownBy(() -> enrollment.confirm(now))
                .isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void promoteFromWaitlist_transitions_to_PENDING_and_preserves_appliedAt() {
        Instant appliedAt = Instant.now();
        Enrollment enrollment = Enrollment.waitlist(CLASS_ID, USER_ID, appliedAt);
        Instant promoteAt = appliedAt.plusSeconds(3600);

        enrollment.promoteFromWaitlist(promoteAt);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
        assertThat(enrollment.getAppliedAt()).isEqualTo(appliedAt);
    }
}
