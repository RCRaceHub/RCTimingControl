package dev.monkeypatch.rctiming.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.monkeypatch.rctiming.persistence.vendor.DatabaseVendor;
import org.jooq.ConnectionProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.jooq.autoconfigure.DefaultConfigurationCustomizer;
import org.springframework.boot.jooq.autoconfigure.ExceptionTranslatorExecuteListener;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The one place that knows which database the app runs on (#26). It applies the vendor from
 * {@code rctiming.database.vendor} to the connection pools, Flyway and jOOQ. No other
 * class may name a vendor-specific class or dialect; {@code PersistencePortabilityTest} enforces
 * that.
 *
 * <p>There are two pools on the same database. Flyway and anything inside a write transaction use
 * the write pool, which the vendor may limit to one connection. jOOQ read queries outside a
 * transaction, or inside a {@link ReadTransaction}, use a read-only pool, so they never queue behind
 * the writer.
 */
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {

    /** The transaction manager on the read pool, used by {@link ReadTransaction}. */
    public static final String READ_TRANSACTION_MANAGER = "readTransactionManager";

    private final DatabaseProperties properties;
    private final DatabaseVendor vendor;
    private final String jdbcUrl;
    private LazyConnectionDataSourceProxy readTransactionDataSource;

    /** Creates the data directory if it is missing and works out the JDBC URL both pools share. */
    public DatabaseConfig(DatabaseProperties properties) {
        this.properties = properties;
        this.vendor = properties.vendor();
        Path dataDirectory = properties.effectiveDataDirectory();
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException("Can't create the data directory " + dataDirectory, e);
        }
        this.jdbcUrl = vendor.jdbcUrl(dataDirectory);
    }

    /**
     * The write pool, the primary data source: Flyway and every write transaction use it. Its transactions
     * begin by taking the write lock (so even a read-only {@code @Transactional} on it blocks other writers).
     */
    @Bean
    @Primary
    DataSource dataSource() {
        HikariConfig config = poolConfig("rctiming-write", vendor.maxWriteConnections(),
                vendor.writeConnectionProperties());
        return new HikariDataSource(config);
    }

    /** The read pool: {@code rctiming.database.read-connections} connections, each set to refuse writes. */
    @Bean
    @Qualifier("read")
    DataSource readDataSource() {
        HikariConfig config = poolConfig("rctiming-read", properties.readConnections(), vendor.connectionProperties());
        config.setConnectionInitSql(vendor.readOnlySessionSql());
        return new HikariDataSource(config);
    }

    /**
     * Plain {@code @Transactional}, read-only or not, runs here on the write pool. Declared because the
     * read transaction manager below stops Spring Boot creating its own.
     */
    @Bean
    @Primary
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    @Bean(READ_TRANSACTION_MANAGER)
    PlatformTransactionManager readTransactionManager(@Qualifier("read") DataSource readDataSource) {
        return new JdbcTransactionManager(readTransactionDataSource(readDataSource));
    }

    /** Lets jOOQ pick the write or read connection per call; see {@link TransactionRoutingConnectionProvider}. */
    @Bean
    ConnectionProvider jooqConnectionProvider(DataSource dataSource, @Qualifier("read") DataSource readDataSource) {
        return new TransactionRoutingConnectionProvider(
                dataSource, readDataSource, readTransactionDataSource(readDataSource));
    }

    /**
     * The read pool as the read transaction manager sees it. Lazy, so a {@link ReadTransaction} only takes a
     * read connection when a query runs on it: one nested in a write transaction runs on the write connection
     * and must not hold a read connection, or wait for one, while it does.
     */
    private synchronized LazyConnectionDataSourceProxy readTransactionDataSource(DataSource readDataSource) {
        if (readTransactionDataSource == null) {
            readTransactionDataSource = new LazyConnectionDataSourceProxy(readDataSource);
        }
        return readTransactionDataSource;
    }

    /** Points Flyway at each configured migration location's sub-folder for the chosen vendor. */
    @Bean
    FlywayConfigurationCustomizer vendorMigrationLocations() {
        String[] locations = properties.migrationLocations().stream()
                .map(location -> "classpath:" + location + "/" + vendor.migrationFolder())
                .toArray(String[]::new);
        return configuration -> configuration.locations(locations);
    }

    /** Sets the vendor's SQL dialect on jOOQ. */
    @Bean
    DefaultConfigurationCustomizer vendorJooqDialect() {
        return configuration -> configuration.set(vendor.jooqDialect());
    }

    /**
     * jOOQ reports a failed constraint as Spring's data integrity violation (a duplicate key for a
     * unique or primary key). Other errors get Spring's usual translation.
     */
    @Bean
    ExceptionTranslatorExecuteListener vendorJooqExceptionTranslator() {
        SQLExceptionTranslator vendorTranslator = vendor.exceptionTranslator();
        SQLExceptionTranslator fallback = new SQLExceptionSubclassTranslator();
        SQLExceptionTranslator translator = (task, sql, ex) -> {
            DataAccessException translated = vendorTranslator.translate(task, sql, ex);
            return translated != null ? translated : fallback.translate(task, sql, ex);
        };
        return ExceptionTranslatorExecuteListener.of(context -> translator);
    }

    private HikariConfig poolConfig(String name, int size, Properties connectionProperties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(name);
        config.setJdbcUrl(jdbcUrl);
        config.setDataSourceProperties(connectionProperties);
        config.setMaximumPoolSize(size);
        config.setMinimumIdle(1);
        return config;
    }
}
