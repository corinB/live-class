// Quartz Scheduler 설정 — in-memory JobStore, Asia/Seoul timezone, ClassAutoCloseJob 등록.
package com.example.liveclass.infrastructure.scheduling;

import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class QuartzConfig {

    /**
     * Installs SpringBeanJobFactory so Quartz jobs have their @Autowired fields injected.
     * The default AdaptableJobFactory does not perform Spring autowiring.
     */
    @Bean
    public SchedulerFactoryBeanCustomizer springBeanJobFactoryCustomizer(ApplicationContext ctx) {
        return schedulerFactoryBean -> {
            SpringBeanJobFactory factory = new SpringBeanJobFactory();
            factory.setApplicationContext(ctx);
            schedulerFactoryBean.setJobFactory(factory);
        };
    }

    @Bean
    public Clock systemKstClock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }

    @Bean
    public JobDetail classAutoCloseJobDetail() {
        return JobBuilder.newJob(ClassAutoCloseJob.class)
                .withIdentity("classAutoCloseJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger classAutoCloseTrigger(JobDetail classAutoCloseJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(classAutoCloseJobDetail)
                .withIdentity("classAutoCloseTrigger")
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 5 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("Asia/Seoul"))
                                .withMisfireHandlingInstructionFireAndProceed()
                )
                .build();
    }
}
