package com.geointelli.ai.property.service.manager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.PropertyIngestionService;
import com.geointelli.ai.property.service.service.PropertyService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyIngestionManager {

    private final PropertyIngestionService propertyIngestionService;
    private final PropertyService propertyService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newFixedThreadPool(5);
    private final Semaphore semaphore = new Semaphore(5);

    public void ingestAllBuildings(List<String> folios) {
        
        if (!running.compareAndSet(false, true)) {
            log.warn("Buildings ingestion already running. Skipping...");
            return;
        }

        try {
            int batchSize = 50;

            for (int i = 0; i < folios.size(); i += batchSize) {
                List<String> batch = folios.subList(i, Math.min(i + batchSize, folios.size()));

                List<Future<?>> futures = batch.stream()
                    .map(folio -> executor.submit(() -> {
                        int attempts = 0;

                        while (attempts < 3) {
                            try {
                                semaphore.acquire();
                                propertyIngestionService.ingestBuildings(folio);
                                log.info("Success folio {}", folio);
                                return;
                            } catch (Exception e) {
                                attempts++;
                                log.warn("Retry {} for folio {}", attempts, folio);
                            } finally {
                                semaphore.release();
                            }
                        }

                        log.error("Failed permanently for folio {}", folio);
                    }))
                    .collect(Collectors.toList());

                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        log.error("Batch execution error", e);
                    }
                }

                Thread.sleep(2000);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }
    }

    public void ingestAllFolios(List<String> folios) {

        if (!running.compareAndSet(false, true)) {
            log.warn("Ingestion already running. Skipping...");
            return;
        }

        try {
            int batchSize = 50;

            for (int i = 0; i < folios.size(); i += batchSize) {
                List<String> batch = folios.subList(i, Math.min(i + batchSize, folios.size()));

                List<Future<?>> futures = batch.stream()
                    .map(folio -> executor.submit(() -> {
                        int attempts = 0;

                        while (attempts < 3) {
                            try {
                                semaphore.acquire();
                                propertyIngestionService.ingest(folio);
                                log.info("Success folio {}", folio);
                                return;
                            } catch (Exception e) {
                                attempts++;
                                log.warn("Retry {} for folio {}", attempts, folio);
                            } finally {
                                semaphore.release();
                            }
                        }

                        log.error("Failed permanently for folio {}", folio);
                    }))
                    .collect(Collectors.toList());

                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception e) {
                        log.error("Batch execution error", e);
                    }
                }

                Thread.sleep(2000);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }
    }


    // public void ingestAllFoliosReactive(List<String> folios) {

    //     if (!running.compareAndSet(false, true)) {
    //         log.warn("ingestion already running. skipping...");
    //         return;
    //     }

    //     log.info("starting ingestion for {} folios", folios.size());

    //     Flux.fromIterable(folios)
    //         .delayElements(Duration.ofMillis(300))

    //         .flatMap(folio ->
    //                 propertyIngestionService.ingestReactive(folio)
    //                     .doOnError(e -> log.error("Failed folio {}", folio, e))
    //                     .onErrorResume(e -> Mono.empty())
    //             , 4 
    //         )

    //         .doOnComplete(() -> {
    //             log.info("Ingestion completed");
    //             running.set(false);
    //         })

    //         .doOnError(e -> {
    //             log.error("Global ingestion error", e);
    //             running.set(false);
    //         })
    //         .subscribe();
    // }
}