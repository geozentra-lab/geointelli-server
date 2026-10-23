package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableCandidateResponse;
import com.geointelli.ai.property.service.dto.ComparableDTO;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.dto.ComparableSubjectDTO;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.ComparableCandidateService;
import com.geointelli.ai.property.service.service.ComparableRankingService;
import com.geointelli.ai.property.service.service.ComparableService;

/** Reuses candidate selection and ranks a bounded pool before limiting the response. */
@Service
public class ComparableServiceImpl implements ComparableService {
    private static final int CANDIDATE_POOL_LIMIT = 100;

    private final ComparableCandidateService candidateService;
    private final ComparableRankingService rankingService;
    private final PropertyRepository propertyRepository;

    public ComparableServiceImpl(ComparableCandidateService candidateService,
            ComparableRankingService rankingService, PropertyRepository propertyRepository) {
        this.candidateService = candidateService;
        this.rankingService = rankingService;
        this.propertyRepository = propertyRepository;
    }

    @Override
    @Transactional(readOnly = true, timeout = 30)
    public ComparableResponse findComparables(Long propertyId, ComparableCandidateRequest request) {
        validateRequest(request);
        ComparableCandidateRequest poolRequest = new ComparableCandidateRequest();
        poolRequest.setMonths(request.getMonths());
        poolRequest.setRadiusMeters(request.getRadiusMeters());
        poolRequest.setAreaTolerancePercent(request.getAreaTolerancePercent());
        poolRequest.setBedroomTolerance(request.getBedroomTolerance());
        poolRequest.setLimit(CANDIDATE_POOL_LIMIT);

        ComparableCandidateResponse pool = candidateService.findCandidates(propertyId, poolRequest);
        Property subject = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException("Property not found: " + propertyId));
        List<ComparableDTO> ranked = rankingService.rank(subject, pool.getCandidates(),
            pool.getAsOfDate(), request.getRadiusMeters());
        List<ComparableDTO> selected = new ArrayList<>(ranked.subList(0, Math.min(request.getLimit(), ranked.size())));
        boolean poolLimitReached = pool.getReturnedCount() >= CANDIDATE_POOL_LIMIT;

        List<String> warnings = new ArrayList<>(pool.getWarnings());
        warnings.add("Similarity scores are rule-based points, not a valuation, probability or appraisal confidence.");
        warnings.add("Missing scoring evidence earns no points; scores are not normalized to hide missing data.");
        if (poolLimitReached) {
            warnings.add("The candidate pool limit was reached; other eligible transactions may not have been ranked.");
        }

        ComparableResponse response = new ComparableResponse();
        response.setPropertyId(propertyId);
        response.setSubject(new ComparableSubjectDTO(subject.getId(),
            subject.getCounty() == null ? null : subject.getCounty().getId(), subject.getDorCode(),
            subject.getBedroomCount(), subject.getBathroomCount(), subject.getBuildingHeatedArea(), subject.getYearBuilt()));
        response.setAsOfDate(pool.getAsOfDate());
        response.setSaleDateFrom(pool.getSaleDateFrom());
        response.setSearch(request);
        response.setSelectionVersion(pool.getSelectionVersion());
        response.setRankingVersion(rankingService.getVersion());
        response.setScoringWeights(rankingService.getWeights());
        response.setCandidatePoolLimit(CANDIDATE_POOL_LIMIT);
        response.setConsideredCandidates(pool.getReturnedCount());
        response.setCandidatePoolLimitReached(poolLimitReached);
        response.setMinimumCandidates(pool.getMinimumCandidates());
        response.setReturnedCount(selected.size());
        response.setStatus(selected.size() < pool.getMinimumCandidates() ? "INSUFFICIENT_DATA" : "COMPARABLES_FOUND");
        response.setWarnings(warnings);
        response.setComparables(selected);
        return response;
    }

    private void validateRequest(ComparableCandidateRequest request) {
        if (request == null || request.getLimit() < 3 || request.getLimit() > 100) {
            throw new IllegalArgumentException("limit must be 3-100");
        }
    }
}
