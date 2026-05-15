// 외부 PG 없이 결제 성공만 로그로 기록하는 mock 결제 게이트웨이
package com.example.liveclass.application.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    /**
     * Mock payment charge — always succeeds and logs the enrollment ID.
     * Called outside the DB transaction (ARCHITECTURE §6.2).
     */
    public void charge(UUID enrollmentId) {
        log.info("[MockPayment] charge success for enrollmentId={}", enrollmentId);
    }
}
