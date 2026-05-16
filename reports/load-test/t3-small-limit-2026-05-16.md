# 부하 테스트 결과 — 2026-05-16 (격리 환경 + 한계 탐색)

오픈런 시나리오의 동시 수강신청 부하를 격리 docker 환경에서 점진 + 공격적 탐색. 데이터 무결성 (정원, 중복 방지) 과 사양별 한계 도달 지점 확인.

## 1. 격리 환경 구성

별도 compose project 로 본 작업과 분리.

### A. t3.small 시뮬레이션 (2 vCPU + 2 GiB)
- 파일: `docker-compose.t3-small.yml`
- 포트: app 8081 / postgres 5433 / redis 6380
- 분배 — postgres 0.4cpu/400m, redis 0.3cpu/200m, app 1.3cpu/1400m (heap 1000m)
- 합계 ≈ t3.small

### B. t3.nano 시뮬레이션 (2 vCPU + 0.5 GiB)
- 파일: `docker-compose.t3-nano.yml`
- 포트: app 8082 / postgres 5434 / redis 6381
- 분배 — postgres 0.4cpu/110m, redis 0.3cpu/35m, app 1.3cpu/360m (heap 220m, MaxMetaspace 128m)
- postgres tuning — shared_buffers=32m, work_mem=2m, max_connections=30
- redis maxmemory 20m
- 합계 ≈ t3.nano

## 2. 결과 누적

| 환경 | users | capacity | PENDING | WAITLISTED | 5xx | 시간 | p99 | app mem 최대 | 판정 |
|---|---|---|---|---|---|---|---|---|---|
| 비격리 (host 전체) | 10 | 3 | 3 | 7 | 0 | 1s | 1006ms | n/a | PASS |
| 비격리 | 100 | 10 | 10 | 65 | 25 | 4s | 3.8s | n/a | PASS |
| 비격리 | 1000 | 1 | 1 | 887 | 112 | 16s | 15.9s | n/a | PASS |
| 비격리 | 10000 | 100 | 100 | 8821 | 1079 | 302s | 301s | n/a | PASS |
| **t3.small** | 10000 | 100 | 100 | 8976 | 924 | 303s | 302s | 738 MiB / 2 GiB (37%) | **PASS** |
| **t3.nano** | 50000 | 100 | — | — | — | — | — | **99.96% sustained** | **중단 — Redis 200ms timeout 폭주** |

## 3. t3.nano 한계 분석 (50k/100)

11분 부하 진행 후 사용자 중단. 부하 테스트 자체는 끝까지 가지 않았지만 **명확한 한계 신호 다수 확보**.

### app 컨테이너 동작 패턴

```
[22:25:57] mem=99.98% cpu=158%  (burst 직후 일시 over-measure)
[22:26:29] mem=99.91% cpu=135%
[22:27:33] mem=99.96% cpu=136%
[22:28:04] mem=98.17% cpu=138%  (GC 회수)
[22:29:09] mem=96.45% cpu= 54%  (fail-closed 안정화로 burst 진정)
[22:30:12] mem=92.69% cpu=  4%  (throttle 진입)
[22:31:16] mem=94.25% cpu= 29%
[22:32:51] mem=99.96% cpu= 15%
```

cpu burst 138% 표시는 limit 1.3 = 130% 의 측정 노이즈 / over-measure. GC 가 한계까지 채우고 회수하는 패턴 반복.

### 핵심 한계 — Redis Lettuce 200ms timeout

app 로그에서 다수 발생.

```
Caused by: io.lettuce.core.RedisCommandTimeoutException: Command timed out after 200 millisecond(s)
ERROR ... GlobalExceptionHandler : Redis unavailable — mapping to 503
```

원인.
- t3.nano redis 0.3 cpu / 35m 제약
- app burst 1.3 cpu 의 cgroup throttle
- 메모리 압박 하의 GC pause
- 합쳐서 Lua 호출이 200ms 안에 안 끝남

결과 — Redis fail-closed 정책 (`fix_redis_fail_closed_handler_2026-05-16.md` PR #104 도입) 이 즉시 발동, Lettuce timeout을 `QueryTimeoutException` → HTTP 503 으로 변환. app 컨테이너는 죽지 않고 healthy 유지.

### 결론

| 지표 | 결과 |
|---|---|
| app 컨테이너 생존 | 11분 부하에서 healthy 유지 — **fail-closed 가 자체 방어막** |
| 데이터 무결성 (DB) | 손상 없음 — Redis timeout 발생 시 DB 변경 전에 503 매핑 |
| 운영 가능성 (사용자 관점) | **사실상 불가** — 대부분 요청이 503. 클라이언트 retry 100 회로도 정원 100 자리 못 채울 가능성 |
| 사양 적정선 | **t3.small 이 최소 운영선** — t3.small 격리 환경에서 10k/100 PASS, mem 37% 여유 |

## 4. 종합 권장

- **본 시스템의 단일 EC2 운영 사양 = t3.small 이상**.
- t3.nano 는 데이터 무결성은 깨지지 않지만 503 fail-closed 폭주로 실질 사용 불가.
- 10k 명 동시 신청 + 100 자리 정원의 오픈런 시나리오는 t3.small 에서 5분 (302초) 안에 종료 + 데이터 무결성 PASS.
- 더 큰 부하 (10만 명 등) 검증은 본 사이클 범위 밖, 별도 사이클로 진행 권장.

## 5. 안전장치 정합성 확인

본 부하 테스트에서 다음 보호 메커니즘이 실제 작동을 확인.

1. **Redis ZSET + Lua 정원 제어** — 모든 PASS 시나리오에서 PENDING = capacity 정확
2. **ClassLockService 분산락 fail-closed** — 클라이언트 retry 책임 (ARCHITECTURE §4)
3. **Lettuce 200ms timeout → 503 매핑** — `fix_redis_fail_closed_handler_2026-05-16.md` (PR #104) 의 GlobalExceptionHandler 동작 확인
4. **DB partial unique index** — 중복 신청 0건

전부 정상 작동. 이전 PR 들의 안전장치가 한계 부하에서도 의도대로 동작함이 검증됨.
