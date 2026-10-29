package dev.monkeypatch.rctiming;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared base class for all integration tests.
 *
 * <p>Every test class runs against the same temporary SQLite database, created once per test run.
 * The property source is identical for all subclasses, so Spring Boot's test context cache reuses
 * one ApplicationContext across them, as it did with the shared PostgreSQL container before #26.
 */
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    private static final Path DATA_DIRECTORY;

    static {
        try {
            DATA_DIRECTORY = Files.createTempDirectory("rctiming-test-db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Autowired
    private JdbcTemplate sharedJdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("rctiming.database.data-directory", DATA_DIRECTORY::toString);
    }

    /**
     * A practice session cannot start while a race is running. The database is shared by every test class, so
     * a test that starts practice first finishes any race an earlier class left running.
     */
    protected void finishLeftoverRunningRaces() {
        sharedJdbc.update("update races set status = 'FINISHED' where status = 'RUNNING'");
    }
}
