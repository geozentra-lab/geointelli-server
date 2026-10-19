package com.geointelli.ai.property.service.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /** Bounds simultaneous panorama processing within one backend instance. */
    @Bean(name = "imageBackfillPropertyExecutor")
    public ThreadPoolTaskExecutor imageBackfillPropertyExecutor(
            @Value("${mapillary.backfill.workers:2}") int workers) {
        if (workers < 1 || workers > 8) throw new IllegalArgumentException("mapillary.backfill.workers must be 1..8");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers);
        executor.setMaxPoolSize(workers);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("image-property-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /** Renews claim leases independently of slow image downloads. */
    @Bean(name = "imageBackfillHeartbeatExecutor", destroyMethod = "shutdownNow")
    public ScheduledExecutorService imageBackfillHeartbeatExecutor() {
        return Executors.newScheduledThreadPool(2, task -> {
            Thread thread = new Thread(task, "image-backfill-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Uses a separate worker so image processing does not block CSV imports. */
    @Bean(name = "imageBackfillExecutor")
    public Executor imageBackfillExecutor(
            @Qualifier("imageBackfillPropertyExecutor") Executor propertyExecutor,
            @Qualifier("imageBackfillHeartbeatExecutor")
                ScheduledExecutorService heartbeatExecutor) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("image-backfill-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(name = "importExecutor")
    public Executor importExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("csv-import-");
        executor.initialize();
        return executor;
    }
}
