package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.OpportunityRefreshResultDTO;
import com.geointelli.ai.property.service.dto.OpportunitySignals;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository;
import com.geointelli.ai.property.service.repository.OpportunityScoreJdbcRepository.ScoredProperty;
import com.geointelli.ai.property.service.service.OpportunityScoreRefreshService.Mode;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpportunityScoreRefreshServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final String VERSION = OpportunityScoringService.SCORE_VERSION;

    private final OpportunityScoreJdbcRepository repository = mock(OpportunityScoreJdbcRepository.class);
    private final OpportunityScoringService scoring =
            new OpportunityScoringServiceImpl(mock(OpportunityScoreJdbcRepository.class), Clock.fixed(NOW, ZoneOffset.UTC));
    /** Counts the transactions and runs each callback directly. */
    private final AtomicInteger transactionsOpened = new AtomicInteger();
    private final TransactionOperations transactions = new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            transactionsOpened.incrementAndGet();
            return action.doInTransaction(null);
        }
    };

    private OpportunityScoreRefreshServiceImpl service(int batchSize) {
        return new OpportunityScoreRefreshServiceImpl(repository, scoring, transactions, batchSize,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static OpportunitySignals valued(long id) {
        return new OpportunitySignals(id, 1L, 10L, true, "supported", new BigDecimal("300000"), new BigDecimal("312000"),
                new BigDecimal("330000"), -0.17, "high", 85, "v17+v18", LocalDate.of(2026, 7, 26), "run-" + id);
    }

    private static OpportunitySignals unsupported(long id) {
        return new OpportunitySignals(id, 1L, 10L, true, "unsupported", null, null, null, null, "low", 40, "v17+v18",
                LocalDate.of(2026, 7, 26), "run-" + id);
    }

    @Test
    @DisplayName("Scores the candidates batch by batch after the last ID, each batch in its own transaction")
    void incremental_processesBatchesInOrder() {
        when(repository.findCandidates(0L, 2, false, VERSION)).thenReturn(List.of(valued(3), unsupported(5)));
        when(repository.findCandidates(5L, 2, false, VERSION)).thenReturn(List.of(valued(8), valued(9)));
        when(repository.findCandidates(9L, 2, false, VERSION)).thenReturn(List.of());
        when(repository.deleteWithoutValuation()).thenReturn(4);

        OpportunityRefreshResultDTO result = service(2).refresh(Mode.INCREMENTAL);

        assertEquals("INCREMENTAL", result.mode());
        assertEquals(VERSION, result.scoreVersion());
        assertEquals(4, result.processed());
        assertEquals(3, result.scored());
        assertEquals(1, result.unavailable());
        assertEquals(4, result.removed());
        assertEquals(NOW, result.startedAt());
        assertEquals(0, result.durationMillis());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ScoredProperty>> batches = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).upsert(batches.capture());
        assertEquals(List.of(3L, 5L), batches.getAllValues().get(0).stream().map(s -> s.score().propertyId()).toList());
        assertEquals("UNAVAILABLE", batches.getAllValues().get(0).get(1).score().status());
        assertEquals(NOW, batches.getAllValues().get(1).get(0).score().scoredAt());
        // two batches and the removal, each in its own transaction
        assertEquals(3, transactionsOpened.get());
    }

    @Test
    @DisplayName("A batch shorter than the batch size is the last; no further query is made")
    void shortBatch_endsTheRun() {
        when(repository.findCandidates(0L, 10, true, VERSION)).thenReturn(List.of(valued(1), valued(2)));

        OpportunityRefreshResultDTO result = service(10).refresh(Mode.FULL);

        assertEquals("FULL", result.mode());
        assertEquals(2, result.processed());
        verify(repository).findCandidates(0L, 10, true, VERSION);
        verify(repository, never()).findCandidates(eq(2L), anyInt(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Nothing to score still removes the scores of properties without a valuation")
    void nothingToScore_stillRemovesOrphans() {
        when(repository.findCandidates(0L, 1000, false, VERSION)).thenReturn(List.of());
        when(repository.deleteWithoutValuation()).thenReturn(2);

        OpportunityRefreshResultDTO result = service(1000).refresh(Mode.INCREMENTAL);

        assertEquals(0, result.processed());
        assertEquals(2, result.removed());
        verify(repository, never()).upsert(any());
    }

    @Test
    @DisplayName("A second run while one is in progress is refused with 409, and the guard is released afterwards")
    void concurrentRun_isRefused() {
        OpportunityScoreRefreshServiceImpl service = service(1000);
        when(repository.findCandidates(anyLong(), anyInt(), anyBoolean(), any())).thenAnswer(call -> {
            ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                    () -> service.refresh(Mode.FULL));
            assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
            return List.of();
        });

        service.refresh(Mode.INCREMENTAL);

        reset(repository);
        when(repository.findCandidates(anyLong(), anyInt(), anyBoolean(), any())).thenReturn(List.of());
        assertDoesNotThrow(() -> service.refresh(Mode.INCREMENTAL));
    }

    @Test
    @DisplayName("A failing run releases the guard so the next run can start")
    void failingRun_releasesTheGuard() {
        OpportunityScoreRefreshServiceImpl service = service(1000);
        when(repository.findCandidates(anyLong(), anyInt(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("database down")).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.refresh(Mode.INCREMENTAL));
        assertDoesNotThrow(() -> service.refresh(Mode.INCREMENTAL));
    }

    @Test
    @DisplayName("A missing mode and a batch size below 1 are rejected")
    void invalidInput_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service(10).refresh(null));
        assertThrows(IllegalArgumentException.class, () -> service(0));
    }
}
