// CancellationWindow 폐구간 정책의 경계값 3개(정확히 7일, +1ns, -1ns)를 검증하는 단위 테스트
package com.example.liveclass.domain.enrollment;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class CancellationWindowTest {

    @Test
    void isWithin_returns_true_when_now_equals_paidAt_plus_7_days() {
        Instant paidAt = Instant.now();
        Instant now = paidAt.plus(7, ChronoUnit.DAYS);

        assertThat(CancellationWindow.SEVEN_DAYS.isWithin(paidAt, now)).isTrue();
    }

    @Test
    void isWithin_returns_false_when_now_is_1ns_after_paidAt_plus_7_days() {
        Instant paidAt = Instant.now();
        Instant now = paidAt.plus(7, ChronoUnit.DAYS).plusNanos(1);

        assertThat(CancellationWindow.SEVEN_DAYS.isWithin(paidAt, now)).isFalse();
    }

    @Test
    void isWithin_returns_true_when_now_is_1ns_before_paidAt_plus_7_days() {
        Instant paidAt = Instant.now();
        Instant now = paidAt.plus(7, ChronoUnit.DAYS).minusNanos(1);

        assertThat(CancellationWindow.SEVEN_DAYS.isWithin(paidAt, now)).isTrue();
    }
}
