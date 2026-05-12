# Class auto-close — Quartz scheduled job (endDate 도래 시 OPEN → CLOSED 자동 전이)

- **Assignee:** The Infra Operator
- **Dependencies:** 05_Logic_Implementer_Class_Repository_Service_Controller.md
- **Definition of Done (DoD):**
  - Quartz Scheduler 가 부팅 시 in-memory JobStore (`RAMJobStore`, Asia/Seoul timezone) 로 초기화된다 (ARCHITECTURE §8.1).
  - `ClassAutoCloseJob extends QuartzJobBean` 이 매일 00:05 KST (cron `0 5 0 * * ?`) 에 실행된다.
  - Job 동작: `classRepository.findByStatusAndPeriodEndDateBefore(OPEN, LocalDate.now(ZoneId.of("Asia/Seoul")))` 결과를 순회하며 각 Class 에 `classApplicationService.autoClose(classId, Instant.now())` 호출. `IllegalStateTransitionException` 은 catch + INFO 로깅 후 다음 Class 로 진행 (Creator 수동 close 와의 race 정상 처리).
  - `misfireInstruction = MISFIRE_INSTRUCTION_FIRE_AND_PROCEED` — EC2 가 00:05 시점에 다운된 경우 다음 부팅 시 한 번 실행 (멱등).
  - `Class.autoClose(now)` 메서드 추가 — Creator ID 검증을 우회하는 시스템 호출 전용 (DOCS §6 Class §4 수정안과 일치). `status != OPEN` 이면 `IllegalStateTransitionException`.
  - 통합 테스트: clock 을 2026-05-13 00:05 KST 로 고정 + `endDate = 2026-05-12` 인 OPEN Class 가 CLOSED 로 전이됨을 검증.
  - 충돌 테스트: Creator 가 수동 close 와 Quartz 자동 close 를 동시 트리거 → 정확히 한 건 성공, 다른 건은 예외 catch.

## Action Items (Checklist)

- [ ] `build.gradle` — `spring-boot-starter-quartz` 의존성이 이미 classpath 에 있음. 추가 작업 없음. 확인만.
- [ ] `infrastructure/scheduling/QuartzConfig.java` — `@Configuration`.
  - 첫 줄 한국어 주석 `// Quartz Scheduler 설정 — in-memory JobStore, Asia/Seoul timezone, ClassAutoCloseJob 등록.`
  - `@Bean JobDetail classAutoCloseJobDetail()` — `JobBuilder.newJob(ClassAutoCloseJob.class).withIdentity("classAutoCloseJob").storeDurably().build();`
  - `@Bean Trigger classAutoCloseTrigger(JobDetail classAutoCloseJobDetail)` — `TriggerBuilder.newTrigger().forJob(classAutoCloseJobDetail).withIdentity("classAutoCloseTrigger").withSchedule(CronScheduleBuilder.cronSchedule("0 5 0 * * ?").inTimeZone(TimeZone.getTimeZone("Asia/Seoul")).withMisfireHandlingInstructionFireAndProceed()).build();`
- [ ] `infrastructure/scheduling/ClassAutoCloseJob.java` — `extends QuartzJobBean`.
  - 첫 줄 한국어 주석 `// endDate 경과한 OPEN Class 를 자동 close 시키는 Quartz Job — 매일 00:05 KST 실행.`
  - 의존: `ClassRepository`, `ClassApplicationService`, `Clock`. (Quartz job 은 Spring bean autowiring 가능 — `SpringBeanJobFactory` 가 처리하므로 `@Autowired` 또는 setter 주입.)
  - `executeInternal(JobExecutionContext context)`:
    ```java
    LocalDate todayKst = LocalDate.now(ZoneId.of("Asia/Seoul"));
    List<Class> targets = classRepository.findByStatusAndPeriodEndDateBefore(ClassStatus.OPEN, todayKst);
    int closed = 0, skipped = 0;
    for (Class c : targets) {
        try {
            classApplicationService.autoClose(c.getId(), clock.instant());
            closed++;
        } catch (IllegalStateTransitionException e) {
            log.info("skipped auto-close for {}: already transitioned", c.getId());
            skipped++;
        }
    }
    log.info("ClassAutoCloseJob completed: closed={}, skipped={}", closed, skipped);
    ```
- [ ] `application/clazz/ClassApplicationService.autoClose(UUID classId, Instant now)` 메서드 추가.
  - `@Transactional`. `Class c = classRepository.findById(classId).orElseThrow(ClassNotFoundException::new);`
  - `c.autoClose(now);` (Class 도메인 메서드).
  - `classRepository.save(c);`
  - AFTER_COMMIT 으로 `ClassClosedEvent` 발행.
- [ ] `domain/clazz/Class.autoClose(Instant now)` 메서드 추가.
  - `if (this.status != ClassStatus.OPEN) throw new IllegalStateTransitionException(this.id, "autoClose requires OPEN, but was " + this.status);`
  - `this.status = ClassStatus.CLOSED;`
  - `this.updatedAt = now;`
  - Creator ID 검증 없음 (시스템 호출). 도메인 invariant DOCS §6 Class §4 예외 조항과 일치.
- [ ] `domain/clazz/ClassRepository.findByStatusAndPeriodEndDateBefore(ClassStatus status, LocalDate cutoff)` 추가.
  - JPQL: `@Query("select c from Class c where c.status = :status and c.period.endDate < :cutoff") List<Class> findByStatusAndPeriodEndDateBefore(...)`.
  - Embedded VO 라 `c.period.endDate` 표기. 컬럼명은 `end_date`.
- [ ] (Verify) `infrastructure/scheduling/ClassAutoCloseJobTest.java` — `@SpringBootTest` (Testcontainers Postgres + Redis, Quartz 활성화).
  - 시나리오 1 — endDate=2026-05-12 인 OPEN Class 적재 → Clock 을 `2026-05-13T00:05:00+09:00` 로 모킹 → job manually trigger (`scheduler.triggerJob(jobKey)`) → Class.status == CLOSED.
  - 시나리오 2 — endDate=2026-05-15 인 OPEN Class 적재 → 같은 trigger → Class.status == OPEN (조건 미달).
  - 시나리오 3 — endDate=2026-05-12 인 CLOSED Class → trigger → 영향 없음.
- [ ] (Verify) `infrastructure/scheduling/AutoCloseManualCloseRaceTest.java` — `@SpringBootTest`.
  - endDate=과거 인 OPEN Class 1개.
  - 두 스레드 동시 실행 — 한쪽은 `classApplicationService.close(classId, creatorId)`, 다른 쪽은 `classApplicationService.autoClose(classId)`.
  - 정확히 한 건 성공, 다른 건 `IllegalStateTransitionException`. 최종 status == CLOSED.
- [ ] (Verify) `infrastructure/scheduling/MisfireRecoveryTest.java` — 가능하면 `MISFIRE_INSTRUCTION_FIRE_AND_PROCEED` 동작 검증. Trigger 를 과거 시각으로 등록 → context refresh 후 단 1회 실행 확인.
- [ ] (Verify) `./gradlew bootRun` 로그에 `org.quartz.core.QuartzScheduler` 시작 + `ClassAutoCloseJob` 트리거 등록이 나오는지 수동 확인 (구현 검증 시 1회).
