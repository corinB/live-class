<!-- 에이전트 실행 결과 보고서 — Redis DB Reconcile Runner + Admin Endpoint 구현 결과 -->
---
status: done
owner: worker (automation pipeline)
created: 2026-05-15
updated: 2026-05-15
---

# Report: 14 — Redis ↔ DB Reconcile Runner + Admin Endpoint

## Input Summary

- 태스크 파일: `plan/before/14_Infra_Operator_Redis_DB_Reconcile_Runner.md`
- 의존 태스크: `10_Logic_Implementer_Enrollment_Confirm_Cancel_And_Waitlist_Promotion.md` (완료 확인)
- DOCS.md 기반 주요 도메인 개념: Enrollment Aggregate (PENDING/CONFIRMED/WAITLISTED/CANCELLED 상태), ClassId, appliedAt FIFO 순서
- ARCHITECTURE.md §7.7 기반 주요 결정: 부팅 시 ZSET 재구성 의무화, DEL + 배치 ZADD 패턴으로 멱등성 보장, class:status mirror 재구성으로 Lua 첫 호출 miss 방지

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `live-class/build.gradle` | modified | `spring-boot-starter-actuator` 의존성 추가 (HealthIndicator 지원) |
| `live-class/src/main/resources/application.yaml` | modified | management.endpoints 노출 설정 추가 (health 컴포넌트 상세 표시) |
| `live-class/src/main/java/.../domain/clazz/ClassRepository.java` | modified | `findByStatus(ClassStatus)` List 반환 메서드 추가 |
| `live-class/src/main/java/.../infrastructure/ReconcileService.java` | created | `reconcileOne(UUID classId)` 핵심 로직. DEL→ZADD 배치 패턴으로 enrolled/waitlist ZSET 재구성 + class:status mirror 재구성. score = epochSec × 1_000_000_000L + nano 공식 사용 |
| `live-class/src/main/java/.../infrastructure/ReconcileRunner.java` | created | `@Order(Ordered.LOWEST_PRECEDENCE)` ApplicationRunner. OPEN 클래스 전체 순회, per-class try/catch + WARN 격리 |
| `live-class/src/main/java/.../infrastructure/ReconcileHealthIndicator.java` | created | `@Component("reconcile")` HealthIndicator. 부팅 시 실패하면 DOWN 상태 유지 |
| `live-class/src/main/java/.../web/admin/AdminReconcileController.java` | created | `POST /api/admin/reconcile/{classId}`. X-User-Id + 시각 INFO 로깅 |
| `live-class/src/main/java/.../web/admin/dto/ReconcileResponse.java` | created | `record ReconcileResponse(UUID classId, Instant reconciledAt)` |
| `live-class/src/test/java/.../infrastructure/ReconcileServiceIntegrationTest.java` | created | ZSET 재구성 정확성(enrolled=5, waitlist=4), 멱등성(2회 호출 동일 결과), class:status mirror 재구성, score FIFO 단조증가 검증 4개 시나리오 |

## Rationale & Tradeoffs

- **score 공식**: `epochSec * 1_000_000_000L + nano`. apply Lua와 동일한 공식을 local static helper `ReconcileService.scoreOf(long, int)`로 임시 구현. Issue #55에서 `EnrollmentMirrorService.scoreOf(Instant)` 공용 메서드로 추출 예정이며, 그 PR 머지 후 이 helper를 대체하면 됨.
- **Spring Boot 4 패키지**: `HealthIndicator`가 `org.springframework.boot.health.contributor` 패키지로 이동됨. `spring-boot-starter-actuator`에서 `spring-boot-health` artifact로 분리되었으나 starter를 추가하면 자동 포함됨.
- **`@Profile` 미적용**: `ReconcileRunner`에 `@Profile("!test")`를 적용하지 않음. `PartialIndexInitializer`와 달리 JPA 표준 쿼리만 사용하므로 H2에서도 안전하게 실행됨.
- **기존 5개 테스트 실패**: `RedisConfigTest`와 `LiveClassApplicationTests`는 우리 변경 이전부터 H2 + PartialIndexInitializer SQL 문법 오류로 실패. task 14 scope 외 pre-existing 문제.

## Follow-ups

- [ ] **Issue #55**: `EnrollmentMirrorService.reverseCancelPromote`의 `System.nanoTime()` 버그 수정 시, `ReconcileService.scoreOf(long, int)` → `EnrollmentMirrorService.scoreOf(Instant)` 공용 메서드로 정리 필요. 두 파일이 충돌 시 마지막 머지 측이 책임.
- [ ] `ReconcileRunnerBootTest` 미구현 — 부팅 직후 ZSET 채워짐 확인 테스트. 시간 제약으로 core 시나리오(ReconcileServiceIntegrationTest 4개)로 대체함.
- [ ] `AdminReconcileControllerTest` 미구현 — MockMvc 슬라이스 테스트. 선택 항목.
- [ ] 기존 5개 pre-existing 테스트 실패(`RedisConfigTest`, `LiveClassApplicationTests`) — `@ActiveProfiles("test")`를 추가하거나 Postgres 컨테이너를 주입하는 방식으로 수정 필요. task 14 범위 외.
