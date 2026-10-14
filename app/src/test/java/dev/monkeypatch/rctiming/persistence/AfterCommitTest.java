package dev.monkeypatch.rctiming.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AfterCommitTest {

    private final AtomicInteger runs = new AtomicInteger();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void outsideATransaction_runsAtOnce() {
        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    void inATransaction_waitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(runs::incrementAndGet);
        assertThat(runs).hasValue(0);

        TransactionSynchronizationUtils.triggerAfterCommit();
        assertThat(runs).hasValue(1);
    }

    @Test
    void inATransactionThatRollsBack_neverRuns() {
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(runs::incrementAndGet);
        TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        assertThat(runs).hasValue(0);
    }
}
