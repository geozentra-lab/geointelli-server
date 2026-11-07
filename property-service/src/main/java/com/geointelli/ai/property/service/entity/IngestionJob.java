package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import java.util.UUID;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One background CSV import or Miami-Dade ingestion run with its progress, lease and checkpoint (SCRUM-188).
 * Progress columns are only written through the fenced repository updates, never through dirty checking,
 * so a worker whose lease expired cannot overwrite another worker's progress.
 */
@Entity
@Table(name = "ingestion_jobs")
@Getter
@Setter
public class IngestionJob {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ImportJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IngestionDataset dataset;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 100)
    private String sourceCounty;
    @Column(length = 100)
    private String sourceState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "county_id")
    private County county;

    private int total;
    private int processed;
    private int succeeded;
    private int skipped;
    private int failed;

    @Column(length = 120)
    private String currentStep;
    /** JSON describing where a resumed job continues; its shape depends on the dataset. */
    @Column(name = "checkpoint_json")
    private String checkpointJson;
    /** JSON with the stored inputs and other start parameters. */
    @Column(name = "parameters_json")
    private String parametersJson;

    /** NONE, S3 or LOCAL. */
    @Column(nullable = false, length = 10)
    private String inputStorage;
    /** Folder or key prefix holding the job's input files. */
    @Column(length = 500)
    private String inputLocation;
    /** Instance that holds LOCAL inputs; only that instance may run or retry the job. */
    @Column(length = 120)
    private String inputScope;

    @Column(length = 120)
    private String workerId;
    private Instant leaseExpiresAt;

    private int attempt = 1;
    private UUID retryOfJobId;
    @Column(length = 120)
    private String createdBy;

    @Column(length = 80)
    private String errorCode;
    @Column(length = 500)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant startedAt;
    private Instant finishedAt;
}
