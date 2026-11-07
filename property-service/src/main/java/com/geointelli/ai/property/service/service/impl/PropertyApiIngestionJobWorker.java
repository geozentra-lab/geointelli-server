package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CountyService;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker;
import com.geointelli.ai.property.service.service.ParcelService;
import com.geointelli.ai.property.service.service.PropertyIngestionService;
import com.geointelli.ai.property.service.service.PropertyIngestionService.FolioResult;
import com.geointelli.ai.property.service.service.PropertyIngestionService.SourceApiException;
import com.geointelli.ai.property.service.service.PropertyService;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs Miami-Dade API ingestion jobs: selects the folios still missing the dataset, then loads them in
 * batches with a bounded pool, a few attempts per folio and a pause between batches, as the previous
 * manager did. Progress is written after every batch; a retry simply re-selects what is still missing.
 */
@Service
@Slf4j
public class PropertyApiIngestionJobWorker implements IngestionJobWorker {
    static final String DATABASE_ERROR = "DATABASE_ERROR";
    static final String INGESTION_ERROR = "INGESTION_ERROR";

    private final PropertyIngestionService ingestionService;
    private final ParcelService parcelService;
    private final PropertyService propertyService;
    private final PropertyRepository propertyRepository;
    private final CountyService countyService;
    private final AsyncTaskExecutor executor;
    private final IngestionProperties.Jobs settings;

    public PropertyApiIngestionJobWorker(PropertyIngestionService ingestionService, ParcelService parcelService,
            PropertyService propertyService, PropertyRepository propertyRepository, CountyService countyService,
            @Qualifier("ingestionApiExecutor") AsyncTaskExecutor executor, IngestionProperties properties) {
        this.ingestionService = ingestionService;
        this.parcelService = parcelService;
        this.propertyService = propertyService;
        this.propertyRepository = propertyRepository;
        this.countyService = countyService;
        this.executor = executor;
        this.settings = properties.getJobs();
    }

    @Override
    public boolean supports(IngestionJob job) {
        return job.getJobType() == ImportJobType.PROPERTY_INGESTION;
    }

    @Override
    public Result run(IngestionJobContext context) throws Exception {
        IngestionDataset dataset = context.job().getDataset();
        List<String> folios = candidates(dataset, countyService.miamiDade().getId());
        Counters counters = new Counters(folios.size(), 0, 0, 0);
        context.progress(counters, "selecting folios", null);
        log.info("Ingestion job {} selected {} folios for {}", context.jobId(), folios.size(), dataset);

        for (int start = 0; start < folios.size(); start += settings.getApiBatchSize()) {
            context.checkWorker();
            List<String> batch = folios.subList(start, Math.min(start + settings.getApiBatchSize(), folios.size()));
            List<Future<FolioOutcome>> outcomes = new ArrayList<>(batch.size());
            for (String folio : batch) outcomes.add(executor.submit(() -> attempt(dataset, folio)));
            int ingested = 0, skipped = 0, failed = 0;
            for (Future<FolioOutcome> future : outcomes) {
                FolioOutcome outcome = await(future);
                if (outcome.failed()) {
                    failed++;
                    context.item(outcome.folio(), "FAILED", outcome.reason(), outcome.message());
                } else if (outcome.ingested()) {
                    ingested++;
                } else {
                    skipped++;
                    context.item(outcome.folio(), "SKIPPED", outcome.reason(), null);
                }
            }
            counters = counters.plus(ingested, skipped, failed);
            int end = start + batch.size();
            context.progress(counters, "folio " + end + " of " + folios.size(), "{\"processedFolios\":" + end + "}");
            if (end < folios.size() && settings.getApiBatchPauseMillis() > 0) Thread.sleep(settings.getApiBatchPauseMillis());
        }
        return counters.failed() > 0 ? Result.partial() : Result.completed();
    }

    /** Folios the dataset is still missing, selected the way the ingestion endpoints always did. */
    private List<String> candidates(IngestionDataset dataset, Long countyId) {
        return switch (dataset) {
            case API_PROPERTIES -> {
                Set<String> existing = new HashSet<>(propertyService.getAllFolios(countyId));
                yield parcelService.getAllFolios(countyId).stream().filter(folio -> !existing.contains(folio)).toList();
            }
            case API_BUILDINGS -> propertyService.getAllFolios(countyId);
            case API_EXTRA_FEATURES -> propertyService.getAllFoliosForPropertyWithoutExtraFeatures(countyId);
            case API_SALES -> propertyRepository.findFoliosWithoutSales(countyId);
            case API_ADDRESSES -> propertyRepository.findFoliosWithoutAddress(countyId);
            default -> throw new IllegalArgumentException("Not an API dataset: " + dataset);
        };
    }

    private FolioOutcome attempt(IngestionDataset dataset, String folio) {
        String reason = INGESTION_ERROR;
        String message = null;
        for (int attempt = 1; attempt <= settings.getApiAttempts(); attempt++) {
            try {
                FolioResult result = ingestionService.ingestForJob(dataset, folio);
                return new FolioOutcome(folio, result.ingested(), false, result.reason(), null);
            } catch (SourceApiException apiError) {
                reason = apiError.getCode();
                message = apiError.getMessage();
            } catch (DataAccessException databaseError) {
                reason = DATABASE_ERROR;
                message = CsvIngestionJobWorker.sanitize(databaseError.getMostSpecificCause().getMessage());
            } catch (RuntimeException error) {
                reason = INGESTION_ERROR;
                message = CsvIngestionJobWorker.sanitize(error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            log.warn("Attempt {} failed for folio {} ({})", attempt, folio, reason);
        }
        return new FolioOutcome(folio, false, true, reason, message);
    }

    private FolioOutcome await(Future<FolioOutcome> future) throws InterruptedException {
        try {
            return future.get();
        } catch (ExecutionException unexpected) {
            return new FolioOutcome("?", false, true, INGESTION_ERROR,
                CsvIngestionJobWorker.sanitize(String.valueOf(unexpected.getCause())));
        }
    }

    private record FolioOutcome(String folio, boolean ingested, boolean failed, String reason, String message) { }
}
