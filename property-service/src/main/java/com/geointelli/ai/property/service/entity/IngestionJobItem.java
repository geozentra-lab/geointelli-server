package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One failed or skipped record of an ingestion job: a CSV row or a folio, with a reason code and a short message. */
@Entity
@Table(name = "ingestion_job_items")
@Getter
@Setter
public class IngestionJobItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID jobId;

    /** {@code file.csv:row} for CSV rows, the folio for API ingestion. */
    @Column(nullable = false, length = 160)
    private String itemKey;

    /** FAILED or SKIPPED. */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 80)
    private String reason;

    @Column(length = 500)
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
