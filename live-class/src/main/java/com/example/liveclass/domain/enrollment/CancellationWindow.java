// 수강 취소 가능 시간 창 Value Object — 폐구간 정책(paidAt + window >= now)으로 취소 가능 여부를 결정
package com.example.liveclass.domain.enrollment;

import java.time.Duration;
import java.time.Instant;

public record CancellationWindow(Duration window) {

    public static final CancellationWindow SEVEN_DAYS = new CancellationWindow(Duration.ofDays(7));

    public CancellationWindow {
        if (window == null) {
            throw new IllegalArgumentException("CancellationWindow window must not be null");
        }
    }

    public boolean isWithin(Instant paidAt, Instant now) {
        return !now.isAfter(paidAt.plus(window));
    }
}
