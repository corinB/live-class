// 실제 PG 연동 없이 로그만 출력하는 모의 결제 게이트웨이
package com.example.liveclass.application.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    public void charge(UUID enrollmentId) {
        log.info("MockPaymentGateway.charge: enrollmentId={}", enrollmentId);
    }
}
