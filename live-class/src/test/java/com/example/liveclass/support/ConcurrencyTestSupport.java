// CountDownLatch ready→go starting gun 패턴으로 N 스레드 race 시나리오를 일반화한 테스트 헬퍼
package com.example.liveclass.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * 동시성 테스트 공용 헬퍼. ExecutorService + CountDownLatch ready→go starting gun 패턴.
 *
 * <p>모든 thread 가 {@code ready.countDown()} 으로 도착 신호를 보낸 뒤 {@code go.await()} 에 묶인다.
 * 마지막 thread 도착을 확인한 메인 thread 가 {@code go.countDown()} 으로 일제 출발시킨다.
 * Race 직전 setup 분산을 최소화해 실제 경합 시점을 좁힌다.
 *
 * <p>throw 된 예외는 모두 잡아 첫 발생만 {@link #runConcurrently(int, Runnable)} 가 다시 throw 한다.
 * thread 별 상태가 필요하면 caller 가 외부 collection (ConcurrentLinkedQueue 등) 으로 직접 수집한다.
 */
public final class ConcurrencyTestSupport {

    private ConcurrencyTestSupport() {
        // static factory
    }

    /** N 스레드가 동시에 같은 task 를 실행. ready→go starting gun 패턴. */
    public static void runConcurrently(int threadCount, Runnable task) {
        runConcurrently(threadCount, i -> task.run());
    }

    /** N 스레드가 동시에 각자의 index (0..n-1) 를 받아 task 를 실행. */
    public static void runConcurrently(int threadCount, IntConsumer indexedTask) {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    try {
                        indexedTask.accept(idx);
                    } catch (Throwable t) {
                        firstError.compareAndSet(null, t);
                    }
                });
            }
            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Threads failed to reach starting gun within 5s");
            }
            go.countDown();
            pool.shutdown();
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow();
                throw new AssertionError("Concurrency task did not finish within 60s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Concurrency runner interrupted", e);
        }

        Throwable err = firstError.get();
        if (err instanceof RuntimeException re) {
            throw re;
        } else if (err != null) {
            throw new RuntimeException(err);
        }
    }
}
