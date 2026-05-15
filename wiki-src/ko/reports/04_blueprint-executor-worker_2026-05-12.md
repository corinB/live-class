<!-- 에이전트 실행 결과 보고서 템플릿 — 입력 요약·수행 내용·근거·후속 작업을 300~500 단어로 기술한다 -->
---
status: done
owner: blueprint-executor-worker
created: 2026-05-12
updated: 2026-05-12
---

# Report: Task 04 — Class Aggregate Entity + Value Objects + State Transition Tests

## Input Summary

- 태스크 파일: `plan/before/04_Logic_Implementer_Class_Domain_Entity.md`
- DOCS.md 기반 주요 도메인 개념:
  - Class Aggregate Root (`Class` entity + 4 VOs + `ClassStatus` enum).
  - Class Invariants §1–§5: capacity>=1, endDate>=startDate, DRAFT→OPEN→CLOSED 일방향, creator만 전이, capacity 변경은 DRAFT만.
  - `UserId` VO는 task 03에서 이미 구현된 공유 VO.
- ARCHITECTURE.md 기반 주요 결정:
  - `@Version Long version` — optimistic lock 준비 (§2.4).
  - `DRAFT → OPEN → CLOSED` 일방향 전이 (§2.4).

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `domain/clazz/ClassId.java` | created | UUID 래퍼 record VO — of()/newId() 정적 팩토리 포함 |
| `domain/clazz/ClassStatus.java` | created | DRAFT/OPEN/CLOSED enum |
| `domain/clazz/Money.java` | created | @Embeddable 일반 클래스 — amount/currency(String), 음수 금지 |
| `domain/clazz/Capacity.java` | created | @Embeddable 일반 클래스 — value>=1 불변 |
| `domain/clazz/ClassPeriod.java` | created | @Embeddable 일반 클래스 — endDate>=startDate 불변 |
| `domain/clazz/IllegalStateTransitionException.java` | created | DomainException 상속, HTTP 409 |
| `domain/clazz/AccessDeniedDomainException.java` | created | DomainException 상속, HTTP 403 |
| `domain/clazz/Class.java` | created | JPA @Entity, draft() 정적 팩토리, open/close/changeCapacity/isOpenForEnrollment, setter 없음, @Version |
| `test/domain/clazz/ClassTest.java` | created | 순수 JUnit5 — 7 시나리오 |
| `test/domain/clazz/MoneyTest.java` | created | VO invariant 위반 케이스 |
| `test/domain/clazz/CapacityTest.java` | created | VO invariant 위반 케이스 |
| `test/domain/clazz/ClassPeriodTest.java` | created | VO invariant 위반 케이스 |
| `plan/before/04_Logic_Implementer_Class_Domain_Entity.md` | modified | 체크리스트 전체 - [ ] → - [x] |
| `reports/04_blueprint-executor-worker_2026-05-12.md` | created | 본 리포트 |

## Rationale & Tradeoffs

(a) **VO를 record가 아닌 일반 @Embeddable 클래스로 구현한 이유 — Hibernate 7 호환성.** Work-order hints §2에서 명시한 fallback 권장을 따른다. Hibernate 7 + Spring Boot 4에서 `@Embeddable record`는 아직 불안정하므로 private no-arg 생성자 + final 필드 + Lombok `@Getter @EqualsAndHashCode @ToString` 조합의 일반 클래스로 구현했다.

(b) **`Money.currency` 필드를 `String`으로 저장한 이유 — JPA/DB 호환성.** `java.util.Currency`는 JPA가 직접 매핑할 수 없어 `getCurrencyCode()` 문자열로 저장한다. `getCurrencyAsObject()` 메서드로 원본 Currency 객체를 복원할 수 있다.

(c) **`Class.java` 네이밍 — Java 키워드 충돌 회피.** `java.lang.Class`와의 충돌을 패키지 이름 `domain.clazz`으로 해소한다 (work-order 명시). 완전 한정 이름(FQN) 사용 시 혼동이 없다.

(d) **`open()`/`close()`/`changeCapacity()` 시그니처에 `UserId requester` 포함 이유.** DOCS.md §3.1의 원형 시그니처(`open(Instant now)`)보다 work-order Implementation hints의 구체 명세(`open(UserId, Instant)`)를 우선했다. Creator 검증을 집중화하여 `checkCreator()` private 메서드로 DRY 처리.

(e) **테스트 로컬 실행 결과 — BLOCKED (Korean path).** `ClassNotFoundException` 발생. `compileTestJava`는 성공, `.class` 파일도 생성됨. `plan/nested-launching-ripple.md`에 문서화된 dev-machine 한계이며 CI(Linux)에서 검증된다.

## Follow-ups

- [ ] task 05 (Class repository/service/controller): `Class` entity를 `@Repository`로 노출.
- [ ] task 16 (Quartz AutoClose): `Class.autoClose(Instant now)`가 필요할 경우 creator 검증을 우회하는 메서드를 별도 추가 — work-order §8.2에서 task 16에서 확정하기로 명시.
- [ ] CI Linux 환경에서 4개 테스트 클래스 통과 여부 확인 (로컬 Korean path BLOCKED).
