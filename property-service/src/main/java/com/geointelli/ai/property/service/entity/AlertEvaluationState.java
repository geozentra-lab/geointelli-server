package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Durable scan cursor and baseline; only the worker holding the parent rule lock may modify it. */
@Entity
@Table(name = "alert_evaluation_states")
@Getter
@Setter
public class AlertEvaluationState {
    @Id
    private Long ruleId;
    private long generation;
    private Long baselineGeneration;
    private String baselineHash;
    private String runHash;
    private String runStamp;
    private long cursorId;
    private long matchedCount;
    private boolean running;
    private String status = "READY";
    private Instant startedAt;
    private Instant lastEvaluatedAt;
    private Instant nextEvaluationAt;
    private String lastErrorCode;
    private int failureCount;
}
