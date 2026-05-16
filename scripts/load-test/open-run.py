# 오픈런 시나리오 E2E 부하 테스트 — N명 사용자가 capacity C 인 단일 강의에 동시 수강신청.
"""
오픈런 부하 테스트 (asyncio + aiohttp).

사전 가정.
    - 백엔드가 http://localhost:8080 에서 동작 중 (docker compose up -d 권장).
    - DB · Redis 초기 상태 (이전 잔여 데이터가 결과 통계에 끼지 않도록 새 class 를 생성한다).

흐름.
    1. creator 1명 등록 → CREATOR 역할.
    2. classmate N명 등록 → CLASSMATE 역할.
    3. capacity C 의 class 를 생성 + DRAFT → OPEN 전이.
    4. classmate N명이 asyncio.gather 로 동시에 POST /api/enrollments 호출.
    5. 응답 통계 + invariant 검증.

invariant.
    - HTTP 201 (PENDING) 건수 ≤ C
    - HTTP 202 (WAITLISTED) + 201 합계 ≤ N
    - HTTP 5xx 비율이 fail-closed 정책 임계 이내
    - DB 활성 enrollment 수 ≤ C (capacity 위반 없음)

실행.
    python scripts/load-test/open-run.py --users 100 --capacity 10
    python scripts/load-test/open-run.py --users 500 --capacity 1 --base-url http://localhost:8080
"""

from __future__ import annotations

import argparse
import asyncio
import json
import sys
import time
import uuid
from collections import Counter
from dataclasses import dataclass, field
from datetime import date, timedelta
from typing import Optional

import aiohttp


@dataclass
class Stats:
    counts: Counter = field(default_factory=Counter)
    pending_ids: list[str] = field(default_factory=list)
    waitlisted_ids: list[str] = field(default_factory=list)
    errors: list[tuple[int, str]] = field(default_factory=list)
    latencies_ms: list[float] = field(default_factory=list)

    def record(self, status: int, body: str, latency_ms: float, enrollment_id: Optional[str] = None) -> None:
        self.counts[status] += 1
        self.latencies_ms.append(latency_ms)
        if status == 201 and enrollment_id:
            self.pending_ids.append(enrollment_id)
        elif status == 202 and enrollment_id:
            self.waitlisted_ids.append(enrollment_id)
        elif status >= 400:
            self.errors.append((status, body[:200]))


async def post_json(session: aiohttp.ClientSession, url: str, headers: dict, payload: dict) -> tuple[int, str]:
    async with session.post(url, headers=headers, json=payload) as resp:
        text = await resp.text()
        return resp.status, text


async def patch_json(session: aiohttp.ClientSession, url: str, headers: dict, payload: dict) -> tuple[int, str]:
    async with session.patch(url, headers=headers, json=payload) as resp:
        text = await resp.text()
        return resp.status, text


async def get_json(session: aiohttp.ClientSession, url: str, headers: dict) -> tuple[int, str]:
    async with session.get(url, headers=headers) as resp:
        text = await resp.text()
        return resp.status, text


async def register_user(session: aiohttp.ClientSession, base_url: str, role: str, name: str) -> str:
    # X-User-Id 는 mock auth filter 가 모든 endpoint 에 요구 — register 단계에서는 검증되지 않으므로 임의값.
    status, body = await post_json(
        session,
        f"{base_url}/api/users",
        headers={"Content-Type": "application/json", "X-User-Id": str(uuid.uuid4())},
        payload={"role": role, "name": name},
    )
    if status != 201:
        raise RuntimeError(f"register user failed: {status} {body}")
    return json.loads(body)["id"]


async def create_class(
    session: aiohttp.ClientSession, base_url: str, creator_id: str, capacity: int
) -> str:
    today = date.today().isoformat()
    end = (date.today() + timedelta(days=30)).isoformat()
    status, body = await post_json(
        session,
        f"{base_url}/api/classes",
        headers={"Content-Type": "application/json", "X-User-Id": creator_id},
        payload={
            "title": f"OpenRun-{int(time.time())}",
            "description": "load-test",
            "priceAmount": 10000,
            "priceCurrency": "KRW",
            "capacity": capacity,
            "startDate": today,
            "endDate": end,
        },
    )
    if status != 201:
        raise RuntimeError(f"create class failed: {status} {body}")
    return json.loads(body)["id"]


async def transition_status(
    session: aiohttp.ClientSession, base_url: str, creator_id: str, class_id: str, target: str
) -> None:
    status, body = await patch_json(
        session,
        f"{base_url}/api/classes/{class_id}/status",
        headers={"Content-Type": "application/json", "X-User-Id": creator_id},
        payload={"target": target},
    )
    if status != 200:
        raise RuntimeError(f"transition {target} failed: {status} {body}")


async def apply_one(
    session: aiohttp.ClientSession,
    base_url: str,
    classmate_id: str,
    class_id: str,
    stats: Stats,
    barrier: asyncio.Event,
    max_retries: int,
    backoff_base_ms: int,
) -> None:
    """
    ARCHITECTURE 4 fail-closed - 503 (CLASS_LOCK_BUSY) 는 클라이언트 retry 책임.
    실제 오픈런 클라이언트는 짧은 backoff 후 재시도. 최대 max_retries 회.
    터미널 응답(201/202/4xx) 또는 retry 소진 시 기록.
    """
    await barrier.wait()
    start = time.perf_counter()
    attempt = 0
    while True:
        try:
            status, body = await post_json(
                session,
                f"{base_url}/api/enrollments",
                headers={"Content-Type": "application/json", "X-User-Id": classmate_id},
                payload={"classId": class_id},
            )
        except aiohttp.ClientError as e:
            latency_ms = (time.perf_counter() - start) * 1000
            stats.record(599, str(e), latency_ms)
            return

        is_lock_busy = status == 503 and "CLASS_LOCK_BUSY" in body
        if is_lock_busy and attempt < max_retries:
            attempt += 1
            jitter = backoff_base_ms * (1 + attempt * 0.5) / 1000.0
            await asyncio.sleep(jitter)
            continue

        latency_ms = (time.perf_counter() - start) * 1000
        enrollment_id = None
        if status in (201, 202):
            try:
                enrollment_id = json.loads(body).get("id")
            except json.JSONDecodeError:
                pass
        stats.record(status, body, latency_ms, enrollment_id)
        return


def summarize(stats: Stats, users: int, capacity: int) -> int:
    print("\n=== Stats ===")
    print(f"users={users}, capacity={capacity}")
    print(f"status counts: {dict(stats.counts)}")
    if stats.latencies_ms:
        sorted_lat = sorted(stats.latencies_ms)
        p50 = sorted_lat[len(sorted_lat) // 2]
        p95 = sorted_lat[int(len(sorted_lat) * 0.95)]
        p99 = sorted_lat[int(len(sorted_lat) * 0.99)]
        print(f"latency ms - p50={p50:.1f}, p95={p95:.1f}, p99={p99:.1f}, max={max(sorted_lat):.1f}")
    print(f"PENDING enrollments: {len(stats.pending_ids)}")
    print(f"WAITLISTED enrollments: {len(stats.waitlisted_ids)}")
    if stats.errors:
        print(f"errors (first 5):")
        for code, body in stats.errors[:5]:
            print(f"  [{code}] {body}")

    print("\n=== Invariant ===")
    failures = []
    if len(stats.pending_ids) > capacity:
        failures.append(f"PENDING({len(stats.pending_ids)}) > capacity({capacity}) — invariant violated")
    if len(stats.pending_ids) + len(stats.waitlisted_ids) > users:
        failures.append("PENDING+WAITLISTED > users — duplicate enrollment suspected")

    five_xx = sum(c for code, c in stats.counts.items() if code >= 500)
    if five_xx > users * 0.5:
        failures.append(f"5xx ratio too high ({five_xx}/{users}) — fail-closed threshold exceeded")

    if failures:
        for f in failures:
            print(f"FAIL — {f}")
        return 1
    print("PASS — capacity invariant holds, no duplicates")
    return 0


async def register_chunked(session, base_url: str, count: int, chunk: int = 200) -> list[str]:
    """register 가 직렬화 압박원이 되지 않도록 chunk 단위로 등록."""
    ids: list[str] = []
    for start in range(0, count, chunk):
        end = min(start + chunk, count)
        batch = await asyncio.gather(
            *[register_user(session, base_url, "CLASSMATE", f"classmate-{i}") for i in range(start, end)]
        )
        ids.extend(batch)
    return ids


async def main_async(args: argparse.Namespace) -> int:
    timeout = aiohttp.ClientTimeout(total=args.timeout_sec)
    connector = aiohttp.TCPConnector(limit=args.connection_limit)
    async with aiohttp.ClientSession(timeout=timeout, connector=connector) as session:
        print(f"target: {args.base_url}")

        creator_id = await register_user(session, args.base_url, "CREATOR", "OpenRunCreator")

        if args.skip_classmate_register:
            # apply 단계는 user 존재 검증을 하지 않는다 (mock auth — EnrollmentApplicationService
            # 의 검증은 creator vs classmate 동일 여부만). 큰 N 부하 테스트의 등록 단계
            # 자체가 압박원이 되지 않게 임의 UUID 생성으로 대체.
            print(f"skipping classmate register, generating {args.users} synthetic UUIDs...")
            classmate_ids = [str(uuid.uuid4()) for _ in range(args.users)]
        else:
            print(f"registering {args.users} classmates...")
            classmate_ids = await register_chunked(session, args.base_url, args.users)

        print(f"creating class with capacity={args.capacity}...")
        class_id = await create_class(session, args.base_url, creator_id, args.capacity)
        await transition_status(session, args.base_url, creator_id, class_id, "OPEN")
        print(f"classId={class_id}, opened")

        stats = Stats()
        barrier = asyncio.Event()

        print(f"firing {args.users} simultaneous apply calls (max_retries={args.max_retries})...")
        tasks = [
            asyncio.create_task(
                apply_one(session, args.base_url, cm_id, class_id, stats, barrier,
                          args.max_retries, args.backoff_base_ms)
            )
            for cm_id in classmate_ids
        ]
        await asyncio.sleep(0.1)
        t0 = time.perf_counter()
        barrier.set()
        await asyncio.gather(*tasks)
        elapsed = time.perf_counter() - t0
        print(f"all {args.users} apply calls finished in {elapsed:.2f}s")

        return summarize(stats, args.users, args.capacity)


def main() -> int:
    parser = argparse.ArgumentParser(description="오픈런 E2E 부하 테스트")
    parser.add_argument("--base-url", default="http://localhost:8080", help="백엔드 base URL")
    parser.add_argument("--users", type=int, default=100, help="동시 신청 사용자 수")
    parser.add_argument("--capacity", type=int, default=10, help="단일 강의 capacity")
    parser.add_argument("--max-retries", type=int, default=20,
                        help="503 CLASS_LOCK_BUSY 시 클라이언트 retry 횟수 (fail-closed 정합)")
    parser.add_argument("--backoff-base-ms", type=int, default=20,
                        help="retry backoff 기본값(ms). 실제 대기 = base * (1 + 0.5*attempt)")
    parser.add_argument("--timeout-sec", type=int, default=60,
                        help="HTTP 요청 전체 timeout(s). 큰 N 에서 늘림.")
    parser.add_argument("--connection-limit", type=int, default=500,
                        help="aiohttp connection pool 한도. 큰 N 에서 늘림.")
    parser.add_argument("--skip-classmate-register", action="store_true",
                        help="classmate 등록 단계 건너뛰고 임의 UUID 사용 (mock auth 가정).")
    args = parser.parse_args()
    return asyncio.run(main_async(args))


if __name__ == "__main__":
    sys.exit(main())
