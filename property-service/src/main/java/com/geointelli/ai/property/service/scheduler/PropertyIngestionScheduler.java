package com.geointelli.ai.property.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.geointelli.ai.property.service.manager.PropertyIngestionManager;
import com.geointelli.ai.property.service.service.ParcelService;

@Component
@RequiredArgsConstructor
@Slf4j
public class PropertyIngestionScheduler {

    private final PropertyIngestionManager manager;
    private final ParcelService parcelService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void run() {
        log.info("Scheduled ingestion started");
        // manager.ingestAllFolios(parcelService.getAllFolios());
    }
}