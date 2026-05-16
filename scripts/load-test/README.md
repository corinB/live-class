# 오픈런 부하 테스트 (E2E)

수강신청이 단일 강의에 동시 폭주하는 상황을 시뮬레이션하는 Python 부하 테스트.

## 사전 준비

```powershell
# 백엔드 + 의존 인프라 기동
$env:COMPOSE_PROFILES = "db,redis,back"
docker compose up -d

# 헬스체크
curl http://localhost:8080/health

# Python 의존성
pip install aiohttp
```

## 실행

```powershell
# 기본 — 100명 동시 신청, capacity 10
python scripts/load-test/open-run.py

# 정원 1자리 1만명 오픈런
python scripts/load-test/open-run.py --users 10000 --capacity 1

# 원격 백엔드 대상
python scripts/load-test/open-run.py --base-url http://remote:8080 --users 500 --capacity 50
```

## 검증 invariant

| 항목 | 기준 |
|---|---|
| PENDING (201) 건수 | ≤ capacity. **위반 시 FAIL** — Lua 정원 제어 깨짐 |
| PENDING + WAITLISTED 합계 | ≤ users. 위반 시 중복 신청 의심 |
| 5xx 비율 | ≤ 50%. 503 fail-closed 는 정상이지만 임계 초과 시 FAIL |
| latency p50/p95/p99 | 출력만. 회귀 추적용 |

## 응답 코드 의미

| 코드 | 의미 | 의도 |
|---|---|---|
| 201 | PENDING | 정원 안 — 결제 대기 |
| 202 | WAITLISTED | 정원 초과 → 대기열 |
| 409 | DuplicateEnrollment / 상태 충돌 | 정상 거부 |
| 503 | ClassLockBusy / MirrorUnavailable | Redis ZSET 또는 분산락 충돌 — fail-closed |
| 5xx (기타) | 백엔드 오류 | 회귀 가능성 |

## 의도된 한계

- 인증은 mock (`X-User-Id` 헤더) — 실제 운영 환경 적용 불가.
- 같은 classmate 가 같은 class 에 1번만 신청 가능 → 본 스크립트는 사용자마다 신규 classmate 생성으로 우회.
- N 이 매우 클 경우 사용자 등록 API 도 부하원이 됨 — pre-register 후 ID list 캐시 변형 가능.

## 출력 예시

```
target: http://localhost:8080
registering creator + 100 classmates...
creating class with capacity=10...
classId=..., opened
firing 100 simultaneous apply calls...
all 100 apply calls finished in 1.42s

=== Stats ===
users=100, capacity=10
status counts: {201: 10, 202: 89, 503: 1}
latency ms — p50=42.1, p95=180.4, p99=320.2, max=412.0
PENDING enrollments: 10
WAITLISTED enrollments: 89
errors (first 5):
  [503] ClassLockBusy ...

=== Invariant ===
PASS — capacity invariant holds, no duplicates
```
