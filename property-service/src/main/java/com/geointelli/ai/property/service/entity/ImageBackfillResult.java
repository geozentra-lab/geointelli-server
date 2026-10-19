package com.geointelli.ai.property.service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Stores the outcome for one property within a backfill job. */
@Entity
@Table(name = "image_backfill_results")
@IdClass(ImageBackfillResultId.class)
@Getter
@Setter
public class ImageBackfillResult {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ImageBackfillJob job;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 80)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "image_id")
    private PropertyImage image;
}
