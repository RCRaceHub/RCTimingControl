package dev.monkeypatch.rctiming.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTest {

    private final AtomicInteger runs = new AtomicInteger();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void outsideATransaction_runsAtOnce() {
        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void insideATransaction_runsOnCommitOnly() {
        beginTransaction();

        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(runs).hasValue(1);
    }

    @Test
    void onARollback_neverRuns() {
        beginTransaction();

        AfterCommit.run(runs::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(runs).hasValue(0);
    }

    @Test
    void workThatFailsAfterTheCommit_isLoggedAndTheNextWorkStillRuns() {
        beginTransaction();

        AfterCommit.run(() -> {
            throw new IllegalStateException("broker down");
        });
        AfterCommit.run(runs::incrementAndGet);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(runs).hasValue(1);
    }

    private static void beginTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }
}
