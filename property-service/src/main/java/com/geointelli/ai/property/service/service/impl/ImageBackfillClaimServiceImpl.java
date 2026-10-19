package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.ImageBackfillRequest;
import com.geointelli.ai.property.service.repository.ImageBackfillClaimRepository;
import com.geointelli.ai.property.service.repository.ImageBackfillJobRepository;
import com.geointelli.ai.property.service.service.ImageBackfillClaimService;
import lombok.RequiredArgsConstructor;

/** Keeps claim transactions separate from downloads and image projection. */
@Service
@RequiredArgsConstructor
@Transactional
public class ImageBackfillClaimServiceImpl implements ImageBackfillClaimService {
    private final ImageBackfillClaimRepository claims;
    private final ImageBackfillJobRepository jobs;

    @Override
    public List<Long> claim(Long jobId, ImageBackfillRequest request) {
        if (jobs.renewLease(jobId) == 0) throw new IllegalStateException("Job lease expired");
        List<Long> selected = new ArrayList<>();
        for (Long id : claims.findAvailable(request.getCountyId(), request.getAfterPropertyId(), request.getBatchSize())) {
            if (claims.claim(id, jobId) == 1) selected.add(id);
        }
        return selected;
    }

    @Override
    public boolean renew(Long jobId) {
        if (jobs.renewLease(jobId) == 0) return false;
        claims.renew(jobId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean owns(Long jobId, Long propertyId) { return claims.owns(jobId, propertyId); }

    @Override
    public boolean lockOwned(Long jobId, Long propertyId) {
        return claims.findOwnedForUpdate(jobId, propertyId).isPresent();
    }

    @Override
    public void release(Long jobId) { claims.release(jobId); }

    @Override
    public void interruptExpired() { jobs.interruptExpired(); }
}
