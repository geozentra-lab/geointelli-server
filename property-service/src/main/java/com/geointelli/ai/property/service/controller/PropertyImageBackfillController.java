package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.geointelli.ai.property.service.dto.ImageBackfillJobDTO;
import com.geointelli.ai.property.service.dto.ImageBackfillRequest;
import com.geointelli.ai.property.service.service.PropertyImageBackfillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Exposes asynchronous Mapillary backfill jobs. */
@RestController
@RequestMapping("/api/properties/images/backfill")
@RequiredArgsConstructor
public class PropertyImageBackfillController {
    private final PropertyImageBackfillService backfillService;

    @PostMapping
    public ResponseEntity<ImageBackfillJobDTO> start(@Valid @RequestBody ImageBackfillRequest request) {
        ImageBackfillJobDTO job = backfillService.start(request);
        return ResponseEntity.accepted()
            .location(URI.create("/api/properties/images/backfill/jobs/" + job.getJobId()))
            .body(job);
    }

    @GetMapping("/jobs/{jobId}")
    public ImageBackfillJobDTO get(@PathVariable Long jobId) {
        return backfillService.get(jobId);
    }
}
