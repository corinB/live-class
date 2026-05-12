<!-- Pre-flight 1 작업 보고서 — Docker Compose 의 Postgres/Redis volume을 named → bind-mount 로 전환한 chore commit -->
---
status: complete
owner: Maestro (Opus)
created: 2026-05-12
updated: 2026-05-12
---

# Report: Pre-flight 1 — Docker bind-mount chore

## Input Summary

- 태스크 파일: 없음 (Day 3 작업 시작 시 working tree에 남아 있던 미커밋 변경을 처리하는 pre-flight 단계)
- DOCS.md 기반 주요 도메인 개념: 해당 없음 (인프라 작업)
- ARCHITECTURE.md 기반 주요 결정: 해당 없음
- 트리거: Day 3 첫 세션 진입 시 `git status` 가 3개 modified 파일을 보여 줌 (`docker-compose.yml`, `.gitignore`, `.claudeignore`). 이전 세션에서 누군가 named volume 을 `./data/` bind-mount 로 전환했으나 커밋 안 됨. 사용자 결정으로 별도 chore commit 으로 처리.

## What Was Done

| File | Action | Summary |
|------|--------|---------|
| `docker-compose.yml` | modified | `postgres-data` named volume → `./data/postgres` bind-mount. `redis-data` named volume → `./data/redis` bind-mount. 파일 끝의 `volumes:` 선언 블록 삭제. |
| `.gitignore` | modified | `data/` 항목 추가 — bind-mount state 가 VCS 에 들어가지 않도록 차단. |
| `.claudeignore` | modified | `data/` 항목 추가 — agent context 에서도 제외. |

- 커밋: `fd76427 chore(infra): use ./data bind-mount for local postgres/redis volumes`
- 푸시: `origin/main` 직접 (chore + 단일 파일 카테고리, PR 없이 main 에 land — 사용자 결정).
- CI 결과: `build-test` + `push-image` + `deploy-ec2` 모두 SUCCESS (이미지 변경 없음에도 EC2 deploy 가 새 compose 파일로 재시작).
- EC2 헬스 검증: `curl http://54.180.196.125:8080/actuator/health` 응답 200 (actuator 엔드포인트는 task 01 이전이라 404 가 정상 — Spring Boot 가 응답하는 점만 확인).

## Rationale & Tradeoffs

- **선택**: bind-mount + `.gitignore` 동봉. 개발자가 `./data/` 디렉토리를 직접 검사·삭제할 수 있고 `docker compose down -v` 로 volume 을 잃어버리지 않는다.
- **대안**: named volume 유지 (이전 상태). 부정적이지 않지만 dev 편의성 측면에서 bind-mount 이득이 큼.
- **트레이드오프**: 이전 named volume (`postgres-data`, `redis-data`) 에 저장된 DB/Redis 데이터는 새 bind-mount 경로로 자동 마이그레이션되지 않는다 → 기존 dev DB 가 빈 상태로 시작됨. Day 3 시점에는 아직 도메인 데이터가 없으므로 무해.
- **EC2 영향**: EC2 의 deploy 도 변경된 compose 파일을 받으므로 EC2 의 `~/live-class/data/postgres`, `~/live-class/data/redis` 경로가 새로 생성된다. EC2 도 데이터가 없는 상태였으므로 무해.

## Follow-ups

- [x] (검증) CI green + EC2 4/4 컨테이너 running 확인 — 완료.
- [ ] 추후 EC2 에서 dev DB 데이터 보존이 필요해지면 production-quality persistent volume 전략으로 재전환 검토 (Out-of-scope of hiring assignment).
- [ ] Pre-flight 1 의 chore commit 이 main 직접 push 형태 — 본 채용 과제에서는 일회성으로 수용했으나 향후 모든 chore 도 PR 경유로 일관화 검토 (CONTRIBUTING.md §1 "main 에 직접 push 금지" 규정을 사실상 위반).
