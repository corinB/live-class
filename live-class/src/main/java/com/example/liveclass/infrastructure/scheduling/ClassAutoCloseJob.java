// endDate 경과한 OPEN Class 를 자동 close 시키는 Quartz Job — 매일 00:05 KST 실행.
package com.example.liveclass.infrastructure.scheduling;

import com.example.liveclass.application.clazz.ClassApplicationService;
import com.example.liveclass.domain.clazz.Class;
import com.example.liveclass.domain.clazz.ClassRepository;
import com.example.liveclass.domain.clazz.ClassStatus;
import com.example.liveclass.domain.clazz.IllegalStateTransitionException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Slf4j
public class ClassAutoCloseJob extends QuartzJobBean {

    @Autowired
    @Setter
    private ClassRepository classRepository;

    @Autowired
    @Setter
    private ClassApplicationService classApplicationService;

    @Autowired
    @Setter
    private Clock clock;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        LocalDate todayKst = LocalDate.now(clock);
        List<Class> targets = classRepository.findByStatusAndPeriodEndDateBefore(
                ClassStatus.OPEN, todayKst);
        int closed = 0, skipped = 0;
        for (Class c : targets) {
            try {
                classApplicationService.autoClose(c.getId(), clock.instant());
                closed++;
            } catch (IllegalStateTransitionException | OptimisticLockingFailureException e) {
                log.info("skipped auto-close for {}: already transitioned or concurrent update", c.getId());
                skipped++;
            }
        }
        log.info("ClassAutoCloseJob completed: closed={}, skipped={}", closed, skipped);
    }
}
