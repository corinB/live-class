# Contributing Guide

이 저장소의 브랜치 전략, 커밋 메시지 컨벤션, PR 절차를 정의한다.
모든 기여자(서브 에이전트 포함)는 이 규약을 준수해야 한다.

> **전략 선택 근거.** 1인 개발·단기 채용 과제(마감 2026-05-16)이므로 `develop` 브랜치와
> 별도 릴리스 브랜치를 요구하는 Git Flow는 과잉이다. `main` 보호 브랜치 하나를 기준으로
> 짧은 피처 브랜치를 빠르게 머지하는 **GitHub Flow**를 채택한다.

---

## 1. 브랜치 전략 (GitHub Flow)

### 1.1 보호 브랜치

| 브랜치 | 용도 | 직접 push 허용 |
|--------|------|----------------|
| `main` | 항상 배포 가능한 상태를 유지한다 | **금지** |

`main`에 대한 모든 변경은 **PR + Squash Merge**를 통해서만 반영한다.

### 1.2 작업 브랜치 네이밍 규칙

형식은 다음과 같다.

```
<type>/<task-number>-<slug>
```

- `<type>` — 아래 허용 타입 목록에서 정확히 일치하는 값을 사용한다.
- `<task-number>` — `plan/before/NN_*.md`의 두 자리 NN과 정확히 일치해야 한다.
  예: `plan/before/03_Logic_Implementer_User_Aggregate.md` → `task-03`
- `<slug>` — 소문자 케밥 케이스(kebab-case)로만 구성한다. 대문자·언더스코어·공백을 금지한다.

**허용 타입.**

| 타입 | 브랜치 용도 |
|------|-------------|
| `feature` | 새 기능 구현 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변경 없는 코드 구조 개선 |
| `perf` | 성능 최적화 |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 작업 |
| `chore` | 빌드·의존성·설정 변경 |
| `ci` | GitHub Actions 워크플로우 변경 |

**브랜치 예시 (최소 5개).**

```
feature/task-03-user-entity
feature/task-04-class-domain
fix/task-09-apply-race
refactor/task-10-cancel-logic
docs/task-13-readme-erd
feature/task-05-class-repository-service
chore/task-02-redis-cache-config
test/task-12-concurrency-integration
```

### 1.3 한 브랜치 = 한 태스크 = 한 PR

- 하나의 브랜치는 반드시 `plan/before/NN_*.md` 태스크 파일 하나에 대응한다.
- 하나의 브랜치는 반드시 PR 하나로 머지된다.
- 여러 태스크를 하나의 브랜치에 묶는 것을 금지한다.

### 1.4 머지 정책

- 머지 방식: **Squash Merge** (히스토리를 단일 커밋으로 압축)
- Squash 커밋 메시지는 아래 커밋 컨벤션을 따른다.
- 브랜치 머지 후 작업 브랜치는 즉시 삭제한다.

---

## 2. 커밋 메시지 컨벤션 (Conventional Commits)

### 2.1 형식

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### 2.2 허용 타입

| 타입 | 사용 시점 |
|------|-----------|
| `feat` | 새로운 기능을 추가할 때 |
| `fix` | 버그를 수정할 때 |
| `refactor` | 기능 변경 없이 코드 구조를 개선할 때 |
| `perf` | 응답 속도·처리량 등 성능을 개선할 때 |
| `test` | 테스트 케이스를 추가하거나 수정할 때 |
| `docs` | README·Javadoc·다이어그램 등 문서를 변경할 때 |
| `build` | Gradle, Docker 등 빌드 시스템 설정을 변경할 때 |
| `ci` | GitHub Actions 워크플로우를 추가하거나 수정할 때 |
| `chore` | 의존성 업데이트, 파일 이동 등 기타 유지보수 작업을 할 때 |
| `style` | 포맷팅, 세미콜론 등 코드 동작에 영향 없는 스타일만 변경할 때 |
| `revert` | 이전 커밋을 되돌릴 때 |

### 2.3 scope 예시

프로젝트 도메인 및 레이어 기준으로 사용한다.

`class` | `enrollment` | `user` | `infra` | `ci` | `auth` | `waitlist` | `config`

### 2.4 subject 규칙

- **50자 이내**로 작성한다.
- **영어**, **명령형 현재시제**로 작성한다 (예: `add`, `fix`, `remove`).
- 끝에 **마침표를 금지**한다.
- 언어 정책: subject는 영어 강제, 본문(body)은 한국어 허용.

### 2.5 푸터 규칙

작업 브랜치가 대응하는 태스크 파일을 다음 형식으로 반드시 명시한다.

```
Refs: plan/before/NN_<Role>_<Slug>.md
```

예시.

```
Refs: plan/before/09_Logic_Implementer_Enrollment_Apply_Service_And_Controller.md
```

### 2.6 BREAKING CHANGE 표기

하위 호환성을 깨는 변경이 있을 경우 푸터에 다음과 같이 명시한다.

```
BREAKING CHANGE: <변경 내용을 한 줄로 설명한다>
```

`BREAKING CHANGE`가 포함된 커밋은 타입에 `!`를 붙여 제목에서도 표시한다.

```
feat(enrollment)!: change apply response status from 200 to 201
```

### 2.7 커밋 예시

**좋은 예시.**

```
feat(enrollment): add Redisson lock to prevent last-seat race

Redisson RLock을 사용해 신청 시 정원 초과 race condition을 방지한다.
wait 500ms / lease 3s 정책을 적용한다.

Refs: plan/before/09_Logic_Implementer_Enrollment_Apply_Service_And_Controller.md
```

```
fix(class): validate state transition direction before apply

역방향 상태 전이 시도 시 도메인 예외를 던지도록 수정한다.
OPEN → DRAFT 역전이가 허용되던 결함을 제거한다.

Refs: plan/before/04_Logic_Implementer_Class_Domain_Entity.md
```

```
test(enrollment): add 10-thread concurrency test for last-seat scenario

ExecutorService 10 스레드로 동시 신청 race 시뮬레이션을 추가한다.
10회 연속 통과를 커버리지 기준으로 설정한다.

Refs: plan/before/12_Quality_Guardian_Concurrency_Integration_Tests.md
```

**나쁜 예시.**

```
# 타입 없음, subject가 50자 초과, 마침표 포함, 태스크 참조 없음
기능 추가 및 버그 수정 및 테스트 개선 및 CI 설정 변경 등 여러 가지 작업을 완료했습니다.
```

```
# 여러 관심사를 하나의 커밋에 묶음
fix: fix enrollment and also add user entity and update README
```

```
# 명령형이 아닌 과거형, 마침표 포함, scope 없음
feat: added redisson lock for enrollment.
```

---

## 3. 작업 흐름 요약

```
1. plan/before/NN_*.md 태스크 파일 확인
2. feature/<task-NN>-<slug> 브랜치 생성 (main에서 분기)
3. 작업 → 커밋 (Conventional Commits 준수)
4. ./gradlew test 통과 확인
5. PR 생성 (.github/pull_request_template.md 체크리스트 전부 충족)
6. Gemini AI 자동 리뷰 확인 → P0/P1 발견사항 대응
7. Squash Merge → 브랜치 삭제
```

---

## 4. CI/CD 워크플로우 구조

CI(검증)와 CD(배포)를 두 파일로 분리한다. 한 워크플로우에 묶지 않는 이유는 PR 단계에서 검증만 빠르게 돌리고, 배포는 `main`에 squash merge된 시점에만 트리거하기 위함이다.

### 4.1 파일 분리

| 파일 | 트리거 | paths 정책 | 잡(job) | 책임 |
|------|--------|-----------|---------|------|
| `.github/workflows/ci.yml` | `pull_request` + `push` to `main` | `paths-ignore`: `**/*.md` · `plan/**` · `reports/**` · `.claude/**` · `docs/**` · `.gitignore` · `.claudeignore` · `.gitattributes` · `LICENSE` | `build-test` | `./gradlew test --build-cache --no-watch-fs` 단위·통합 테스트. 도커 이미지 빌드 없음 |
| `.github/workflows/cd.yml` | `push` to `main` (only) | `paths`(화이트리스트): `live-class/**` · `front/**` · `docker-compose.yml` · `Dockerfile` · `live-class/Dockerfile` · `front/Dockerfile` · `.github/workflows/cd.yml` | `push-image` → `deploy-ec2` | 도커 이미지 푸시 후 EC2 배포. PR에서는 절대 동작하지 않음 |

- `ci.yml`은 PR 단계에서 코드 변경의 빌드·테스트 통과를 강제한다. 문서·메타 경로(`paths-ignore` 대상)는 CI에서 스킵된다 — 모든 변경에 CI가 도는 것이 아님을 주의.
- `cd.yml`은 squash merge가 끝난 직후 `main` 기준으로만 배포 파이프라인을 실행한다. `paths` 화이트리스트로 실제 소스·인프라 변경에서만 트리거되며, PR HEAD에서 시크릿이 노출될 위험을 제거한다.
- `cd.yml`의 `deploy-ec2`는 `push-image` 성공을 `needs:`로 의존시켜 이미지 빌드 실패 시 배포를 차단한다. **단 build-test와는 `needs:`로 묶이지 않는다.** main push 시 테스트 미통과 상태로 배포되는 것을 막는 것은 아래 §4.2 branch protection 수동 설정에 전적으로 의존한다.

### 4.2 Branch protection 수동 설정

`cd.yml`이 `main` push 보호를 가정하기 때문에, GitHub Settings에서 한 번만 수동 등록한다(저장소 관리자 권한 필요).

1. `Settings` → `Branches` → `Add branch protection rule`.
2. `Branch name pattern`에 `main` 입력.
3. `Require a pull request before merging` 활성화. `Required reviewers`는 기존 정책 그대로 둔다(현재 1인 개발 · self-review 허용).
4. `Require status checks to pass before merging` 활성화 후 검색창에 `Build & Test` 입력 → `ci.yml`의 `build-test` 잡 이름을 선택한다. status check 이름은 워크플로우의 `jobs.<id>.name` 또는 잡 id에 따라 결정되므로, 이름이 다르면 `ci.yml`의 실제 잡 이름을 확인 후 등록한다.
5. `Require branches to be up to date before merging` 활성화 권장.
6. `Do not allow bypassing the above settings` 활성화하여 관리자도 직접 push를 차단한다.

이 설정은 IaC로 관리하지 않는다. 워크플로우 잡 이름을 바꾸면 branch protection의 status check 항목도 함께 갱신해야 한다(`docs/harness/03-migration.md`의 환경 가정 표 참조).

---

## 5. 절대 금지 사항

- `main`에 직접 push하는 것을 금지한다.
- `git push --force` 및 `git reset --hard`를 금지한다 (pre-bash hook이 차단한다).
- PR 체크리스트를 충족하지 않은 상태에서 머지하는 것을 금지한다.
- 하나의 브랜치에 두 개 이상의 태스크를 묶는 것을 금지한다.
- 하드코딩된 패스워드·API Key·DB URL을 커밋하는 것을 금지한다.
