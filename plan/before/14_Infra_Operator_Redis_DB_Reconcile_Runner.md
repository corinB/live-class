# Redis ↔ DB Reconcile Runner + Admin Endpoint

- **Assignee:** The Infra Operator
- **Dependencies:** 10_Logic_Implementer_Enrollment_Confirm_Cancel_And_Waitlist_Promotion.md
- **Definition of Done (DoD):**
  - 부팅 시 `ReconcileRunner extends ApplicationRunner` 가 `OPEN` 상태의 모든 Class 에 대해 ZSET (`enrolled:{classId}`, `waitlist:{classId}`) 를 DB 활성 enrollment 로부터 재구성한다 (ARCHITECTURE §7.7).
  - 멱등 — 두 번 실행해도 결과 동일. `DEL` + 배치 `ZADD` 패턴.
  - `POST /api/admin/reconcile/{classId}` 엔드포인트로 운영자가 단일 강의에 대해 강제 재구성 가능. 호출자(`X-User-Id`) 와 시각을 로그로 남긴다 (mock 환경, 별도 인증 없음).
  - `class:status:{classId}` mirror 도 함께 재구성 (Lua 가 첫 호출에서 mirror miss 를 겪지 않도록).
  - 부팅 시 reconcile 이 실패해도 application 부팅 자체는 막지 않는다. WARN 로깅 + actuator `/health` 의 `reconcile` 컴포넌트를 DOWN 으로 표시.

## Action Items (Checklist)

- [ ] `infrastructure/ReconcileRunner.java` — `@Component` `implements ApplicationRunner`.
  - 첫 줄 한국어 주석 `// 부팅 시 OPEN 상태 Class 의 Redis ZSET mirror 를 DB 로부터 재구성하는 ApplicationRunner.`
  - `@Order(Ordered.LOWEST_PRECEDENCE)` — 다른 ApplicationRunner 보다 늦게.
  - 의존: `ClassRepository`, `EnrollmentRepository`, `StringRedisTemplate`.
  - `run(ApplicationArguments args)` 안에서 `classRepository.findByStatus(ClassStatus.OPEN)` 순회 → 각 Class 에 대해 `reconcileOne(classId)` 호출. 예외는 per-class 격리 (try/catch + WARN).
- [ ] `infrastructure/ReconcileService.java` — `@Service`. 본 로직 분리(부팅 + admin endpoint 둘 다 호출).
  - `void reconcileOne(UUID classId)`:
    - `redisTemplate.delete(List.of("enrolled:"+classId, "waitlist:"+classId));`
    - `List<Enrollment> activeEnrolled = enrollmentRepository.findByClassIdAndStatusInOrderByAppliedAtAsc(classId, List.of(PENDING, CONFIRMED));`
    - `List<Enrollment> activeWait = enrollmentRepository.findByClassIdAndStatusInOrderByAppliedAtAsc(classId, List.of(WAITLISTED));`
    - 각 리스트를 `redisTemplate.opsForZSet().add(key, Set<TypedTuple>)` 로 batch ZADD (score=`epochSec*1e9 + nano`, value=`classmateId.toString()`).
    - `Class clazz = classRepository.findById(classId).orElseThrow(...);`
    - `redisTemplate.opsForValue().set("class:status:"+classId, clazz.getStatus().name(), Duration.ofMinutes(5));`
    - INFO 로그 — `reconciled classId={}, enrolled={}, waitlist={}`.
- [ ] `web/admin/AdminReconcileController.java` — `@RestController @RequestMapping("/api/admin")`.
  - `POST /reconcile/{classId}` — `@CurrentUserId UUID caller, @PathVariable UUID classId`.
    - INFO 로그 `manual reconcile triggered classId={}, caller={}` — caller 가 null 이어도 허용 (mock).
    - `reconcileService.reconcileOne(classId);`
    - 200 + `{"classId": "...", "reconciledAt": "..."}`.
- [ ] `web/admin/dto/ReconcileResponse.java` (record).
- [ ] (Verify) `infrastructure/ReconcileServiceTest.java` — `@SpringBootTest` (Testcontainers Postgres + Redis).
  - capacity=3 Class 에 PENDING 2, WAITLISTED 1 적재 → Redis FLUSHDB → `reconcileOne(classId)` 호출 → `ZCARD enrolled == 2` && `ZCARD waitlist == 1` && `ZRANGE` score 가 DB `appliedAt` 순.
  - 같은 reconcile 두 번 호출 → 결과 동일 (멱등).
- [ ] (Verify) `infrastructure/ReconcileRunnerBootTest.java` — `@SpringBootTest` 로 부팅 직후 OPEN Class 의 ZSET 이 채워져 있는지 검증.
- [ ] (Verify) `web/admin/AdminReconcileControllerTest.java` — `@WebMvcTest` 또는 `@SpringBootTest`. 호출 시 200 + reconcileService mock 검증.
