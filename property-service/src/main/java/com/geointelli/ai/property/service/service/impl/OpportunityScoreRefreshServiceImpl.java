package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.OpportunityRefreshResultDTO;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository.ScoredProperty;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scores properties in batches ordered by property ID. Every batch is stored in its own transaction, so a long run
 * keeps its progress and an interrupted run continues where it stopped on the next incremental run.
 */
@Slf4j
@Service
public class OpportunityScoreRefreshServiceImpl implements OpportunityScoreRefreshService {

    private final OpportunityScoreJdbcRepository repository;
    private final OpportunityScoringService scoring;
    private final TransactionOperations transactions;
    private final int batchSize;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public OpportunityScoreRefreshServiceImpl(OpportunityScoreJdbcRepository repository,
                                              OpportunityScoringService scoring,
                                              PlatformTransactionManager transactionManager,
                                              @Value("${opportunity-scores.refresh.batch-size:1000}") int batchSize) {
        this(repository, scoring, new TransactionTemplate(transactionManager), batchSize, Clock.systemUTC());
    }

    OpportunityScoreRefreshServiceImpl(OpportunityScoreJdbcRepository repository, OpportunityScoringService scoring,
                                       TransactionOperations transactions, int batchSize, Clock clock) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("opportunity-scores.refresh.batch-size must be 1 or greater");
        }
        this.repository = repository;
        this.scoring = scoring;
        this.transactions = transactions;
        this.batchSize = batchSize;
        this.clock = clock;
    }

    @Override
    public OpportunityRefreshResultDTO refresh(Mode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode is required");
        }
        if (!running.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An opportunity score refresh is already running");
        }
        try {
            return run(mode);
        } finally {
            running.set(false);
        }
    }

    private OpportunityRefreshResultDTO run(Mode mode) {
        Instant startedAt = clock.instant();
        boolean all = mode == Mode.FULL;
        long after = 0;
        long processed = 0;
        long scored = 0;
        try {
            while (true) {
                List<OpportunitySignals> batch = repository.findCandidates(after, batchSize, all,
                        OpportunityScoringService.SCORE_VERSION);
                if (batch.isEmpty()) {
                    break;
                }
                List<ScoredProperty> results = new ArrayList<>(batch.size());
                for (OpportunitySignals signals : batch) {
                    OpportunityScoreDTO score = scoring.score(signals, startedAt);
                    results.add(new ScoredProperty(signals, score));
                    if (OpportunityScoreDTO.STATUS_SCORED.equals(score.status())) {
                        scored++;
                    }
                }
                transactions.executeWithoutResult(status -> repository.upsert(results));
                processed += batch.size();
                after = batch.get(batch.size() - 1).propertyId();
                if (batch.size() < batchSize) {
                    break;
                }
            }
        } catch (RuntimeException failure) {
            // Logged here with the position because neither the admin endpoint's error response nor the scheduler
            // records the stack trace; the stored batches are kept and the next incremental run continues after them.
            log.error("Opportunity score {} refresh failed after {} stored properties; the failing batch starts after "
                    + "property ID {}", mode, processed, after, failure);
            throw failure;
        }
        Integer removed = transactions.execute(status -> repository.deleteWithoutValuation());
        Instant finishedAt = clock.instant();
        return new OpportunityRefreshResultDTO(mode.name(), OpportunityScoringService.SCORE_VERSION, processed, scored,
                processed - scored, removed == null ? 0 : removed, startedAt, finishedAt,
                Duration.between(startedAt, finishedAt).toMillis());
    }
}
