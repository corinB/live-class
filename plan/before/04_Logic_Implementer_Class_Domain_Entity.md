# Class Aggregate 도메인 엔티티 + Value Objects + 상태전이 단위 테스트

- **Assignee:** The Logic Implementer
- **Dependencies:** 03_Logic_Implementer_User_Aggregate.md
- **Definition of Done (DoD):**
  - `Class` Aggregate Root와 4개 VO(`ClassId`, `Money`, `Capacity`, `ClassPeriod`)가 도메인 레이어에 Spring 의존 없이 구현된다 (DOCS §3.1).
  - 상태 전이 메서드 `draft()`, `open()`, `close()`, `changeCapacity()`, `isOpenForEnrollment()` 5개가 의도 표현 메서드로 구현되고 setter 없음.
  - DOCS §6 Class Invariants 5개 (1: capacity>=1, 2: endDate>=startDate, 3: DRAFT→OPEN→CLOSED 일방향, 4: creator만 전이, 5: capacity 변경은 DRAFT만)가 순수 JUnit 테스트로 검증된다.
  - `@Version` 컬럼이 부여되어 optimistic lock 준비 (ARCHITECTURE §2.4).
  - `IllegalStateTransitionException`이 도메인 예외로 정의되어 HTTP 409로 매핑된다.

## Action Items (Checklist)

- [ ] `domain/clazz/ClassId.java` — `record ClassId(UUID value)` + 정적 팩토리.
- [ ] `domain/clazz/Money.java` — `record Money(BigDecimal amount, Currency currency)`. 생성자에서 `amount.signum() >= 0`, `currency != null` 검증.
- [ ] `domain/clazz/Capacity.java` — `record Capacity(int value)`. 생성자에서 `value >= 1` 검증.
- [ ] `domain/clazz/ClassPeriod.java` — `record ClassPeriod(LocalDate startDate, LocalDate endDate)`. 생성자에서 `endDate >= startDate` 검증.
- [ ] `domain/clazz/ClassStatus.java` — `enum { DRAFT, OPEN, CLOSED }`.
- [ ] `domain/clazz/Class.java` — `@Entity @Table(name="classes")`. `@Embedded` Money/Capacity/ClassPeriod, `@Enumerated(EnumType.STRING) status`, `@Version Long version`.
  - 필드: `id (UUID)`, `title`, `description`, embedded VOs, `status`, `creatorId (UUID)`, `createdAt`, `updatedAt`.
  - private 기본 생성자.
  - `static Class draft(UserId, title, desc, Money, Capacity, ClassPeriod, Instant)`.
  - `void open(UserId requester, Instant now)` — `requester != creatorId` → `AccessDeniedDomainException`, `status != DRAFT` → `IllegalStateTransitionException`.
  - `void close(UserId requester, Instant now)` — `status != OPEN` → `IllegalStateTransitionException`.
  - `void changeCapacity(Capacity, UserId requester, Instant now)` — DRAFT 아니면 예외.
  - `boolean isOpenForEnrollment(Instant now)` — `status == OPEN`.
- [ ] `domain/clazz/IllegalStateTransitionException.java`, `domain/clazz/AccessDeniedDomainException.java` — `DomainException` 상속, status 409 / 403.
- [ ] (Verify) `domain/clazz/ClassTest.java` — 순수 JUnit5.
  - `draft()` → status == DRAFT.
  - `draft().open(creator, now)` → status == OPEN, `ClassOpenedEvent`는 별도 검증 안 함.
  - `open(otherUser, now)` → `AccessDeniedDomainException`.
  - `open()` 두 번 호출 → 두 번째 `IllegalStateTransitionException`.
  - `close()` from DRAFT → 예외.
  - `changeCapacity()` from OPEN → 예외.
  - `isOpenForEnrollment()` 상태별 결과.
- [ ] (Verify) `domain/clazz/MoneyTest.java`, `CapacityTest.java`, `ClassPeriodTest.java` — VO invariant 위반 시 `IllegalArgumentException`.
