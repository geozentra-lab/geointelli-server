package com.geointelli.ai.property.service.config;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Worker identity and executors of the background ingestion jobs (SCRUM-188). */
@Configuration
public class IngestionJobConfig {

    /** Identifies this process (worker ID) and this instance (scope of local inputs). */
    public record IngestionWorkerIdentity(String workerId, String scope) { }

    @Bean
    public IngestionWorkerIdentity ingestionWorkerIdentity(IngestionProperties properties) {
        String scope = properties.getJobs().resolvedInstanceId();
        return new IngestionWorkerIdentity(scope + "-" + UUID.randomUUID().toString().substring(0, 8), scope);
    }

    /** Runs one ingestion job at a time on this instance; other instances run other jobs. */
    @Bean(name = "ingestionJobExecutor")
    public ThreadPoolTaskExecutor ingestionJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("ingestion-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /** Renews job leases independently of slow imports and API calls. */
    @Bean(name = "ingestionHeartbeatExecutor", destroyMethod = "shutdownNow")
    public ScheduledExecutorService ingestionHeartbeatExecutor() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "ingestion-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Bounds the Miami-Dade API calls made at once by one ingestion job, as the previous manager did. */
    @Bean(name = "ingestionApiExecutor")
    public AsyncTaskExecutor ingestionApiExecutor(IngestionProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getJobs().getApiWorkers());
        executor.setMaxPoolSize(properties.getJobs().getApiWorkers());
        executor.setQueueCapacity(properties.getJobs().getApiBatchSize() * 2);
        executor.setThreadNamePrefix("ingestion-api-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
