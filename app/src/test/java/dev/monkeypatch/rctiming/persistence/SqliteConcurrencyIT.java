package dev.monkeypatch.rctiming.persistence;

import dev.monkeypatch.rctiming.AbstractIntegrationTest;
import dev.monkeypatch.rctiming.domain.competitor.Competitor;
import dev.monkeypatch.rctiming.domain.competitor.CompetitorRepository;
import dev.monkeypatch.rctiming.domain.practice.PracticeSessionRepository;
import dev.monkeypatch.rctiming.practice.PracticeSessionService;
import dev.monkeypatch.rctiming.timing.LapPassingEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Laps are written while officials' screens and spectator boards keep reading (#26). With one
 * write connection and WAL, writers queue in the pool and readers never wait on them, so nothing
 * should fail with SQLITE_BUSY ("database is locked").
 */
class SqliteConcurrencyIT extends AbstractIntegrationTest {

    private static final int TRANSPONDERS = 8;
    private static final int PASSINGS_PER_TRANSPONDER = 40;
    private static final int COMPETITOR_WRITES = 100;
    private static final int READERS = 4;

    @Autowired PracticeSessionService practiceSessionService;
    @Autowired PracticeSessionRepository practiceSessionRepository;
    @Autowired CompetitorRepository competitorRepository;
    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired TransactionTemplate transactionTemplate;
    @Autowired TestRestTemplate restTemplate;
    @Autowired @Qualifier("read") DataSource readDataSource;

    @Test
    void lapWritesAndPollingReadersRunTogetherWithoutLockErrors() throws Exception {
        finishLeftoverRunningRaces();
        practiceSessionRepository.findRunningSession()
                .ifPresent(running -> practiceSessionService.stop(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), running.getId()));
        long sessionId = practiceSessionService
                .create(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), "Concurrency", null, 3, null).id();
        practiceSessionService.start(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), sessionId);

        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicInteger reads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2 + READERS);
        try {
            CountDownLatch writersDone = new CountDownLatch(2);
            pool.submit(guarded(failures, start, writersDone, () -> {
                // The decoder thread: one passing per car per lap, each lap its own transaction
                for (int lap = 0; lap < PASSINGS_PER_TRANSPONDER; lap++) {
                    for (int car = 0; car < TRANSPONDERS; car++) {
                        long rtcMicros = (lap * 20_000L + car * 100L) * 1_000L;
                        eventPublisher.publishEvent(new LapPassingEvent(0, String.valueOf(901 + car), rtcMicros));
                    }
                }
            }));
            pool.submit(guarded(failures, start, writersDone, () -> {
                // An official adding walk-ins at the same time
                for (int i = 0; i < COMPETITOR_WRITES; i++) {
                    int n = i;
                    transactionTemplate.executeWithoutResult(tx -> {
                        Competitor competitor = new Competitor();
                        competitor.setDisplayName("Concurrency driver " + n);
                        competitor.setCreatedAt(Instant.now());
                        competitor.setUpdatedAt(Instant.now());
                        competitorRepository.save(competitor);
                    });
                }
            }));
            for (int r = 0; r < READERS; r++) {
                pool.submit(guarded(failures, start, null, () -> {
                    long lastCount = 0;
                    while (writing.get()) {
                        ResponseEntity<String> schedule = restTemplate.getForEntity("/api/v1/events", String.class);
                        assertThat(schedule.getStatusCode()).isEqualTo(HttpStatus.OK);
                        long count = countLaps(sessionId);
                        assertThat(count).as("readers see committed laps only grow").isGreaterThanOrEqualTo(lastCount);
                        lastCount = count;
                        reads.incrementAndGet();
                    }
                }));
            }

            start.countDown();
            assertThat(writersDone.await(2, TimeUnit.MINUTES)).as("writers finished").isTrue();
            writing.set(false);
        } finally {
            writing.set(false);
            pool.shutdown();
            assertThat(pool.awaitTermination(1, TimeUnit.MINUTES)).isTrue();
            practiceSessionService.stop(dev.monkeypatch.rctiming.domain.audit.Actor.system("test"), sessionId);
        }

        assertThat(failures).isEmpty();
        assertThat(reads.get()).as("readers ran while laps were written").isGreaterThan(READERS);
        // The first passing of each car opens its first lap, so it is not stored
        assertThat(countLaps(sessionId)).isEqualTo((long) TRANSPONDERS * (PASSINGS_PER_TRANSPONDER - 1));
    }

    private long countLaps(long sessionId) throws SQLException {
        try (Connection connection = readDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT count(*) FROM practice_laps WHERE practice_session_id = ?")) {
            statement.setLong(1, sessionId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static Runnable guarded(Queue<Throwable> failures, CountDownLatch start, CountDownLatch done,
                                    ThrowingRunnable body) {
        return () -> {
            try {
                start.await();
                body.run();
            } catch (Throwable t) {
                failures.add(t);
            } finally {
                if (done != null) {
                    done.countDown();
                }
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
