// 부팅 시 OPEN 상태 Class 의 Redis ZSET mirror 를 DB 로부터 재구성하는 ApplicationRunner.
package com.example.liveclass.infrastructure;

import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.ClassStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "reconcile.run-at-startup", havingValue = "true", matchIfMissing = true)
public class ReconcileRunner implements ApplicationRunner {

    private final ClassRepository classRepository;
    private final ReconcileService reconcileService;
    private final ReconcileHealthIndicator healthIndicator;

    public ReconcileRunner(ClassRepository classRepository,
                           ReconcileService reconcileService,
                           ReconcileHealthIndicator healthIndicator) {
        this.classRepository = classRepository;
        this.reconcileService = reconcileService;
        this.healthIndicator = healthIndicator;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Class> openClasses = classRepository.findByStatus(ClassStatus.OPEN);
        boolean anyFailure = false;

        for (Class clazz : openClasses) {
            try {
                reconcileService.reconcileOne(clazz.getId());
            } catch (Exception e) {
                log.warn("reconcile failed for classId={}: {}", clazz.getId(), e.getMessage());
                anyFailure = true;
            }
        }

        if (anyFailure) {
            healthIndicator.markDown("One or more classes failed to reconcile at startup");
        } else {
            healthIndicator.markUp();
        }

        log.info("ReconcileRunner completed: total OPEN classes={}", openClasses.size());
    }
}
