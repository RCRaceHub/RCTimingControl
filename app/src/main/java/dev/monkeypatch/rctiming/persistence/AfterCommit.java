package dev.monkeypatch.rctiming.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs work that must not happen unless the current transaction commits: STOMP broadcasts and changes to the
 * in-memory live timing, which a rollback cannot undo (#217). Outside a transaction the work runs at once.
 *
 * <p>The work runs after the commit but before the write connection is handed back, so with one write
 * connection the next write transaction always sees it done. The work must not write to the database: the
 * transaction it would join has already committed. It must not call {@code run} again either, since work
 * registered from inside an after-commit callback never runs.
 *
 * <p>Work that fails after the commit is logged, not thrown: the change it follows is saved, so the caller
 * should not see an error, and the next callbacks still run.
 */
public final class AfterCommit {

    private static final Logger log = LoggerFactory.getLogger(AfterCommit.class);

    private AfterCommit() {
    }

    /**
     * Runs {@code work} once the current transaction has committed, or at once when there is none. If the
     * transaction rolls back the work never runs. A {@link RuntimeException} it throws is logged and swallowed.
     */
    public static void run(Runnable work) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    work.run();
                } catch (RuntimeException e) {
                    log.error("Work after a commit failed; the committed change stands", e);
                }
            }
        });
    }
}
