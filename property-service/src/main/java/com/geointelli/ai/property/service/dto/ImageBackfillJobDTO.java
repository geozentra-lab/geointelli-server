package com.geointelli.ai.property.service.dto;

import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
public class ImageBackfillJobDTO {
    private Long jobId;
    private Long countyId;
    private String status;
    private int batchSize;
    private int total;
    private int processed;
    private int completed;
    private int skipped;
    private int failed;
    private long afterPropertyId;
    private long lastPropertyId;
    private String errorCode;
    private Instant createdAt;
    private Instant finishedAt;
    private List<ImageBackfillResultDTO> results;
}
