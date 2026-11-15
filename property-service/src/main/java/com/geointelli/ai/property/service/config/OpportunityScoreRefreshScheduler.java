package com.geointelli.ai.property.service.config;

import com.geointelli.ai.property.service.dto.OpportunityRefreshResultDTO;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Periodically rescores properties whose stored opportunity score is missing or stale (SCRUM-76), for example
 * after a valuation run. Enabled with {@code opportunity-scores.refresh.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "opportunity-scores.refresh", name = "enabled", havingValue = "true")
public class OpportunityScoreRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(OpportunityScoreRefreshScheduler.class);

    private final OpportunityScoreRefreshService refreshService;

    public OpportunityScoreRefreshScheduler(OpportunityScoreRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @Scheduled(initialDelayString = "${opportunity-scores.refresh.initial-delay-millis:60000}",
            fixedDelayString = "${opportunity-scores.refresh.poll-delay-millis:900000}")
    public void refresh() {
        try {
            OpportunityRefreshResultDTO result = refreshService.refresh(Mode.INCREMENTAL);
            if (result.processed() > 0 || result.removed() > 0) {
                log.info("Opportunity scores refreshed: {} processed, {} scored, {} unavailable, {} removed in {} ms",
                        result.processed(), result.scored(), result.unavailable(), result.removed(),
                        result.durationMillis());
            }
        } catch (ResponseStatusException alreadyRunning) {
            log.debug("Opportunity score refresh skipped: a run is already in progress");
        } catch (RuntimeException failure) {
            log.warn("Opportunity score refresh failed: {}", failure.getClass().getSimpleName());
        }
    }
}
