// ClassRepository 동시성 테스트 — findByIdForUpdate 가 두 번째 트랜잭션을 첫 번째 커밋까지 블로킹하는지 검증
package com.example.liveclass.domain.clazz;

import com.example.liveclass.domain.user.UserId;
import com.example.liveclass.support.IntegrationTest;
import com.example.liveclass.support.PostgresTestContainer;
import com.example.liveclass.support.RedisContainerExtension;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class ClassRepositoryConcurrencyTest {

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        PostgresTestContainer.applyProperties(registry);
        RedisContainerExtension.applyProperties(registry);
    }

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private Class buildDraftClass(UUID creatorId) {
        return Class.draft(
                UserId.of(creatorId),
                "Concurrency Test Class",
                null,
                Money.of(BigDecimal.valueOf(5000), Currency.getInstance("KRW")),
                Capacity.of(5),
                ClassPeriod.of(LocalDate.now(), LocalDate.now().plusDays(3)),
                Instant.now()
        );
    }

    @Test
    void threadB_blocksUntilThreadACommits() throws Exception {
        UUID creatorId = UUID.randomUUID();
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);

        // Persist the class outside the test transactions
        UUID classId = txTemplate.execute(status -> {
            Class c = buildDraftClass(creatorId);
            return classRepository.save(c).getId();
        });

        CountDownLatch aHasLock = new CountDownLatch(1);   // A signals it holds the lock
        CountDownLatch aCanCommit = new CountDownLatch(1); // test signals A to commit

        AtomicLong bStartNanos = new AtomicLong();
        AtomicLong bEndNanos = new AtomicLong();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Thread A: acquire lock, signal, sleep 500 ms, then commit
        Future<?> futureA = executor.submit(() -> {
            txTemplate.execute(status -> {
                classRepository.findByIdForUpdate(classId).orElseThrow();
                aHasLock.countDown();       // tell B we hold the lock
                try {
                    aCanCommit.await();     // wait for test to release us
                    Thread.sleep(500);      // hold lock for 500 ms after signal
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            });
        });

        // Thread B: wait until A has the lock, then try to acquire — should block
        Future<?> futureB = executor.submit(() -> {
            try {
                aHasLock.await(); // ensure A has the lock before B tries
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            bStartNanos.set(System.nanoTime());
            txTemplate.execute(status -> {
                classRepository.findByIdForUpdate(classId).orElseThrow();
                return null;
            });
            bEndNanos.set(System.nanoTime());
        });

        // Wait until A actually holds the DB lock before releasing it
        aHasLock.await();

        // Let A proceed past its sleep (it will hold the lock for 500 ms)
        aCanCommit.countDown();

        futureA.get();
        futureB.get();
        executor.shutdown();

        long elapsedMs = (bEndNanos.get() - bStartNanos.get()) / 1_000_000;
        assertThat(elapsedMs)
                .as("Thread B should have waited at least 400 ms for Thread A to release the lock")
                .isGreaterThanOrEqualTo(400L);
    }
}
