package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Stores one background image job and its progress counters. */
@Entity
@Table(name = "image_backfill_jobs")
@Getter
@Setter
public class ImageBackfillJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "county_id", nullable = false)
    private County county;

    @Column(nullable = false, length = 20)
    private String status;
    private int batchSize;
    private int total;
    private int processed;
    private int completed;
    private int skipped;
    private int failed;
    private long afterPropertyId;
    private long lastPropertyId;
    @Column(length = 80)
    private String errorCode;
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant finishedAt;
    @Column(insertable = false, updatable = false)
    private Instant leaseExpiresAt;

    @OneToMany(mappedBy = "job")
    private List<ImageBackfillResult> results = new ArrayList<>();
}
