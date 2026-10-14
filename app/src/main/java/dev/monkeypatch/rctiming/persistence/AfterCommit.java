package dev.monkeypatch.rctiming.persistence;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work that must not happen unless the current transaction commits: STOMP broadcasts and changes to the
 * in-memory live timing, which a rollback cannot undo (#217). Outside a transaction the work runs at once.
 *
 * <p>The work runs after the commit but before the write connection is handed back, so with one write
 * connection the next write transaction always sees it done. It must not write to the database itself:
 * called from another transaction's after-commit callback, the work would also be lost.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }
}
