<!-- 라이브 강의 수강신청 백엔드의 REST API 엔드포인트 / 요청·응답 예시 / 에러 코드 명세 -->
# API 명세 — 라이브 강의 수강신청 백엔드

> Swagger UI — `http://localhost:8080/swagger-ui.html` (런타임).
> OpenAPI JSON — `http://localhost:8080/v3/api-docs`.

## 공통

| 항목 | 값 |
|---|---|
| Base URL (로컬) | `http://localhost:8080` |
| 인증 헤더 | `X-User-Id: <UUID>` — 모든 보호 엔드포인트 필수 (mock 인증) |
| Content-Type | `application/json` (UTF-8) |
| 에러 응답 포맷 | RFC 7807 `ProblemDetail` + `errorCode` 확장 |

### 공통 에러 코드

| HTTP | errorCode | 의미 |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Bean Validation 위반 |
| 400 | `MALFORMED_JSON` | JSON parse 실패 (Content-Type charset / 문법) |
| 401 | (해당 없음) | `X-User-Id` 헤더 누락 |
| 403 | 도메인별 | 권한 없음 (예: 다른 사용자의 enrollment 조작) |
| 404 | 도메인별 | 리소스 미존재 |
| 409 | `OPTIMISTIC_LOCK_FAILURE` | `@Version` 충돌, retry 권장 |
| 503 | `MIRROR_UNAVAILABLE` | Redis 다운 / 명령 timeout — retry 권장 |
| 503 | `CLASS_LOCK_BUSY` | 동일 classId 다른 요청 진행 중 — backoff retry 권장 |
| 500 | `INTERNAL_ERROR` | 분류되지 않은 예외 |

---

## 1. User

### 1.1 회원 등록

```
POST /api/users
```

| 항목 | 값 |
|---|---|
| 인증 | 불필요 |
| 본문 | `{ "role": "CREATOR" \| "CLASSMATE", "name": "string" }` |

요청 예.

```json
{
  "role": "CLASSMATE",
  "name": "홍길동"
}
```

응답 201.

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "role": "CLASSMATE",
  "name": "홍길동",
  "createdAt": "2026-05-16T10:23:45Z"
}
```

에러.

| HTTP | 조건 |
|---|---|
| 400 | `role` 미지정 / `name` 공백 / `name` 50자 초과 |

### 1.2 본인 정보 조회

```
GET /api/users/me
X-User-Id: <UUID>
```

응답 200 — 위 등록 응답과 동일 스키마.

---

## 2. Class

### 2.1 강의 생성 (DRAFT)

```
POST /api/classes
X-User-Id: <CREATOR-UUID>
```

본문.

```json
{
  "title": "Spring Boot 4 Live",
  "description": "Java 21 + Spring Boot 4 실전",
  "price": { "value": 49000, "currency": "KRW" },
  "capacity": 50,
  "period": { "startDate": "2026-06-01", "endDate": "2026-06-30" }
}
```

응답 201.

```json
{
  "id": "22222222-2222-2222-2222-222222222222",
  "title": "Spring Boot 4 Live",
  "description": "...",
  "price": { "value": 49000, "currency": "KRW" },
  "capacity": 50,
  "period": { "startDate": "2026-06-01", "endDate": "2026-06-30" },
  "status": "DRAFT",
  "creatorId": "...",
  "createdAt": "2026-05-16T10:30:00Z",
  "version": 0
}
```

에러.

| HTTP | 조건 |
|---|---|
| 400 | `title` 200자 초과 / `capacity < 1` / `endDate < startDate` |
| 403 | `X-User-Id` 의 role 이 CREATOR 가 아님 |

### 2.2 상태 전이

```
PATCH /api/classes/{id}/status
X-User-Id: <CREATOR-UUID>
```

본문.

```json
{ "target": "OPEN" }
```

응답 200 — 갱신된 Class. `@Version` 충돌 시 1회 자동 retry, 그래도 실패하면 409 `OPTIMISTIC_LOCK_FAILURE`.

전이 규칙.

| 현재 → 목표 | 허용 |
|---|---|
| DRAFT → OPEN | O — `class:status:{id}` mirror 갱신 |
| OPEN → CLOSED | O — WAITLISTED 일괄 cancel (DOCS §49-62 의도, 본 사이클 listener stub) |
| DRAFT → CLOSED | X |
| CLOSED → * | X (역방향 전이 금지) |

에러.

| HTTP | errorCode | 조건 |
|---|---|---|
| 403 | (도메인) | 본인이 생성한 강의가 아님 |
| 409 | `OPTIMISTIC_LOCK_FAILURE` | retry 후에도 충돌 |

### 2.3 단건 조회

```
GET /api/classes/{id}
```

응답 200 — 강의 상세.

### 2.4 OPEN 강의 목록

```
GET /api/classes?page=0&size=20
```

응답 200.

```json
{
  "content": [{ "id": "...", "title": "...", "status": "OPEN", "capacity": 50, ... }],
  "page": 0,
  "size": 20,
  "totalElements": 123,
  "totalPages": 7
}
```

### 2.5 강의별 수강생 목록 (CONFIRMED)

```
GET /api/classes/{id}/students?page=0&size=20
X-User-Id: <CREATOR-UUID>
```

본인이 생성한 강의에 대해서만 조회 가능 (다른 사용자는 403).

응답 200 — CONFIRMED 상태 enrollment 의 학생 정보. `paidAt` 오름차순 정렬.

---

## 3. Enrollment

### 3.1 신청

```
POST /api/enrollments
X-User-Id: <CLASSMATE-UUID>
```

본문.

```json
{ "classId": "22222222-2222-2222-2222-222222222222" }
```

응답 — `status` 에 따라 HTTP 코드 분기.

| 결과 | HTTP | status |
|---|---|---|
| 정원 미달 | 201 | `PENDING` |
| 정원 만석 | 202 | `WAITLISTED` |

응답 본문 (201 예).

```json
{
  "id": "33333333-3333-3333-3333-333333333333",
  "classId": "...",
  "classmateId": "...",
  "status": "PENDING",
  "appliedAt": "2026-05-16T11:00:00Z",
  "paidAt": null,
  "cancelledAt": null,
  "version": 0
}
```

에러.

| HTTP | errorCode | 조건 |
|---|---|---|
| 400 | (도메인) | classId 의 강의가 OPEN 이 아님 — `CLASS_NOT_OPEN` |
| 403 | (도메인) | Creator 가 본인 강의에 신청 시도 |
| 409 | (도메인) | 활성 상태의 동일 `(classId, classmateId)` 중복 — `DUPLICATE_ACTIVE` |
| 503 | `CLASS_LOCK_BUSY` | 동일 classId 다른 요청 진행 중 |
| 503 | `MIRROR_UNAVAILABLE` | Redis 다운 |

### 3.2 결제 확정 (mock)

```
POST /api/enrollments/{id}/confirm-payment
X-User-Id: <CLASSMATE-UUID>
```

응답 200 — `status: CONFIRMED`, `paidAt: <now>`.

에러.

| HTTP | 조건 |
|---|---|
| 403 | 본인 신청이 아님 |
| 409 | `OPTIMISTIC_LOCK_FAILURE` — retry 후에도 실패 |
| (도메인) | PENDING 가 아닌 상태에서 호출 — `IllegalStateTransitionException` |

### 3.3 취소

```
DELETE /api/enrollments/{id}
X-User-Id: <CLASSMATE-UUID>
```

응답 200 — `status: CANCELLED`, `cancelledAt: <now>`. CONFIRMED 취소 시 가장 오래된 WAITLISTED 가 자동 PENDING 으로 승격됨 (`enrollment_cancel_promote.lua`).

규칙.

| 현재 status | 결과 |
|---|---|
| PENDING / WAITLISTED | 즉시 CANCELLED |
| CONFIRMED + `now ≤ paidAt + 7일` | CANCELLED + 다음 WAITLISTED 승격 |
| CONFIRMED + `now > paidAt + 7일` | 400 — `OutsideCancellationWindowException` |
| 이미 CANCELLED | 200 idempotent (status 그대로 반환) |

에러.

| HTTP | errorCode | 조건 |
|---|---|---|
| 400 | `OUTSIDE_CANCELLATION_WINDOW` | 7일창 초과 |
| 403 | (도메인) | 본인 신청이 아님 |
| 503 | `CLASS_LOCK_BUSY` | 동일 classId 다른 요청 진행 중 |

### 3.4 본인 수강 목록

```
GET /api/enrollments/me?status=PENDING,CONFIRMED&page=0&size=20
X-User-Id: <CLASSMATE-UUID>
```

`status` 쿼리는 옵션 — 콤마 구분 다중 값. 미지정 시 모든 상태.

응답 200 — `Page<EnrollmentResponse>` 형태.

---

## 4. Admin

### 4.1 ZSET 강제 재구성

```
POST /api/admin/reconcile/{classId}
X-User-Id: <UUID>
```

ZSET (`enrolled:{classId}`, `waitlist:{classId}`) 를 DB 기준으로 재구성. apply / cancel 과 동일 lock key 를 공유하므로 contention 시 skip.

응답.

| HTTP | 의미 |
|---|---|
| 200 | 정상 실행 — `{ "classId": "...", "executedAt": "..." }` |
| 409 | 다른 apply/cancel 진행 중 — `reconcile in progress for classId=...` |

---

## 5. 헬스 / 라우팅 검증

| Method | Path | 응답 |
|---|---|---|
| GET | `/health` | `{ "status": "ok" }` |
| GET | `/api/ping` | `{ "message": "ping" }` |
| GET | `/api/pong` | `{ "message": "pong" }` |
| GET | `/actuator/health` | Spring Boot Actuator 기본 |

---

## 6. cURL 빠른 시작

```bash
# 1. CREATOR 등록
CREATOR=$(curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"role":"CREATOR","name":"강사"}' | jq -r .id)

# 2. CLASSMATE 등록
STUDENT=$(curl -s -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"role":"CLASSMATE","name":"학생"}' | jq -r .id)

# 3. 강의 생성 + OPEN
CLASS=$(curl -s -X POST http://localhost:8080/api/classes \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $CREATOR" \
  -d '{
    "title":"Spring Boot 4 Live",
    "price":{"value":49000,"currency":"KRW"},
    "capacity":50,
    "period":{"startDate":"2026-06-01","endDate":"2026-06-30"}
  }' | jq -r .id)

curl -s -X PATCH http://localhost:8080/api/classes/$CLASS/status \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $CREATOR" \
  -d '{"target":"OPEN"}'

# 4. 신청 + 결제
ENR=$(curl -s -X POST http://localhost:8080/api/enrollments \
  -H "Content-Type: application/json" \
  -H "X-User-Id: $STUDENT" \
  -d "{\"classId\":\"$CLASS\"}" | jq -r .id)

curl -s -X POST http://localhost:8080/api/enrollments/$ENR/confirm-payment \
  -H "X-User-Id: $STUDENT"
```
