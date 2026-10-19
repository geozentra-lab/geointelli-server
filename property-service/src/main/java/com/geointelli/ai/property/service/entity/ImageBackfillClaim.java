package com.geointelli.ai.property.service.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Assigns a property to one job until its claim expires or is released. */
@Entity
@Table(name = "image_backfill_claims")
@Getter
@Setter
public class ImageBackfillClaim {
    @Id
    private Long propertyId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private ImageBackfillJob job;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant expiresAt;
}
