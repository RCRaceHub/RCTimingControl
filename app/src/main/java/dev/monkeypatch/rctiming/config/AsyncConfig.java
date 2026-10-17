package dev.monkeypatch.rctiming.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Thread pools for background work: the general {@code @Async} pool and the single timing thread that every decoder
 * passing runs on (see the decoder flow in {@code DecoderListener}). Scheduled jobs use Spring's own scheduler pool,
 * sized by {@code spring.task.scheduling.pool.size}, not these.
 */
@Configuration
public class AsyncConfig {

    /**
     * The default pool for {@code @Async} methods. Named so that {@code @EnableAsync} can resolve it
     * unambiguously alongside the STOMP broker's own executor beans. A full queue throws
     * {@code RejectedExecutionException} to the caller, so nothing that must not be lost goes here.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        return executor;
    }

    /**
     * Runs every decoder passing, one at a time and in the order the decoder sent them.
     *
     * <p>A lap time is the gap between a car's last two passings, so handling two passings out of order
     * would lose a lap and move the car's last passing time backwards. One thread guarantees the order.
     * The queue is unbounded so a passing is never rejected: they are tiny, and a backlog only builds if
     * the database stalls. The decoder's network thread only hands work to this executor and never
     * touches the database.
     */
    @Bean(name = "timingExecutor")
    public Executor timingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setThreadNamePrefix("timing-");
        return executor;
    }
}
