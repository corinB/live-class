// 부팅 시 reconcile 실패 여부를 actuator /health 의 reconcile 컴포넌트로 노출하는 HealthIndicator.
package com.example.liveclass.infrastructure;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

@Component("reconcile")
public class ReconcileHealthIndicator implements HealthIndicator {

    private enum State { UP, DOWN }

    private final AtomicReference<State> state = new AtomicReference<>(State.UP);
    private volatile String downReason = null;

    @Override
    public Health health() {
        if (state.get() == State.DOWN) {
            return Health.down().withDetail("reason", downReason).build();
        }
        return Health.up().build();
    }

    public void markDown(String reason) {
        this.downReason = reason;
        state.set(State.DOWN);
    }

    public void markUp() {
        this.downReason = null;
        state.set(State.UP);
    }
}
