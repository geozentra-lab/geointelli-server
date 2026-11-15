package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.OpportunityBrowseRequest;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO.Filters;
import com.geointelli.ai.property.service.dto.OpportunityPageDTO.Item;
import com.geointelli.ai.property.service.dto.OpportunityScoreDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.entity.PropertyOpportunityScore;
import com.geointelli.ai.property.service.repository.PropertyOpportunityScoreRepository;
import com.geointelli.ai.property.service.repository.PropertyOpportunityScoreRepository.PropertyRow;
import com.geointelli.ai.property.service.service.OpportunityBrowseService;
import com.geointelli.ai.property.service.service.OpportunityScoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pages through the stored opportunity scores; the property details of a page are read in one query. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OpportunityBrowseServiceImpl implements OpportunityBrowseService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final TypeReference<List<OpportunityScoreDTO.Factor>> FACTORS = new TypeReference<>() {
    };

    private final PropertyOpportunityScoreRepository repository;
    private final ObjectMapper json;

    @Override
    public OpportunityPageDTO browse(OpportunityBrowseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        SortOrder sort = validate(request);
        String version = OpportunityScoringService.SCORE_VERSION;
        Page<PropertyOpportunityScore> page = repository.browse(version, request.countyId(), request.stateId(),
                request.minScore(), request.minValue(), request.maxValue(),
                PageRequest.of(request.page(), request.size(), order(sort)));

        Map<Long, PropertyRow> properties = page.isEmpty() ? Map.of()
                : repository.findPropertyRows(page.map(PropertyOpportunityScore::getPropertyId).getContent()).stream()
                .collect(Collectors.toMap(PropertyRow::getPropertyId, Function.identity()));
        Page<Item> items = page.map(score -> toItem(score, properties.get(score.getPropertyId())));

        return new OpportunityPageDTO(version, status(page, version),
                new Filters(request.countyId(), request.stateId(), request.minScore(), request.minValue(),
                        request.maxValue()),
                sort.name(), PageResponse.from(items), OpportunityScoringService.NOTES);
    }

    /** Tells an empty page apart: filtered out, nothing eligible, or not scored yet. */
    private String status(Page<PropertyOpportunityScore> page, String version) {
        if (page.getTotalElements() > 0) {
            return OpportunityPageDTO.STATUS_OK;
        }
        if (!repository.existsByScoreVersion(version)) {
            return OpportunityPageDTO.STATUS_NOT_SCORED;
        }
        if (!repository.existsByScoreVersionAndStatus(version, OpportunityScoreDTO.STATUS_SCORED)) {
            return OpportunityPageDTO.STATUS_NO_ELIGIBLE;
        }
        return OpportunityPageDTO.STATUS_NO_MATCHES;
    }

    private static SortOrder validate(OpportunityBrowseRequest request) {
        if (request.page() < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (request.size() < 1 || request.size() > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
        if ((long) request.page() * request.size() >= MAX_RESULTS) {
            throw new IllegalArgumentException("page is too large: only the first " + MAX_RESULTS
                    + " results can be paged; narrow the filters");
        }
        positive("countyId", request.countyId());
        positive("stateId", request.stateId());
        if (request.minScore() != null
                && (request.minScore().signum() < 0 || request.minScore().compareTo(HUNDRED) > 0)) {
            throw new IllegalArgumentException("minScore must be between 0 and 100");
        }
        notNegative("minValue", request.minValue());
        notNegative("maxValue", request.maxValue());
        if (request.minValue() != null && request.maxValue() != null
                && request.minValue().compareTo(request.maxValue()) > 0) {
            throw new IllegalArgumentException("minValue must not exceed maxValue");
        }
        if (request.sort() == null || request.sort().isBlank()) {
            return SortOrder.SCORE_DESC;
        }
        try {
            return SortOrder.valueOf(request.sort().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("sort must be one of SCORE_DESC, FORECAST_GROWTH_1Y_DESC, VALUE_ASC, VALUE_DESC");
        }
    }

    private static void positive(String name, Long value) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive number");
        }
    }

    private static void notNegative(String name, BigDecimal value) {
        if (value != null && value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be 0 or greater");
        }
    }

    /** Sort columns of the entity; ties end with the property ID so pages never overlap. */
    private static Sort order(SortOrder sort) {
        Sort byScore = Sort.by(Direction.DESC, "score");
        Sort byId = Sort.by(Direction.ASC, "propertyId");
        return switch (sort) {
            case SCORE_DESC -> byScore.and(byId);
            case FORECAST_GROWTH_1Y_DESC -> Sort.by(Direction.DESC, "forecastGrowth1yPct").and(byScore).and(byId);
            case VALUE_ASC -> Sort.by(Direction.ASC, "estimatedValue").and(byScore).and(byId);
            case VALUE_DESC -> Sort.by(Direction.DESC, "estimatedValue").and(byScore).and(byId);
        };
    }

    private Item toItem(PropertyOpportunityScore score, PropertyRow property) {
        List<OpportunityScoreDTO.Factor> factors = json.convertValue(score.getFactors(), FACTORS).stream()
                .map(OpportunityBrowseServiceImpl::twoDecimals).toList();
        return new Item(score.getPropertyId(),
                property == null ? null : property.getFolio(),
                property == null ? null : property.getAddress(),
                property == null ? null : property.getCity(),
                property == null ? null : property.getZip(),
                property == null ? null : property.getCountyName(),
                property == null ? null : property.getStateCode(),
                score.getScore(), OpportunityScoringService.MAX_SCORE, score.getCoveragePercent(),
                score.getEstimatedValue(), score.getForecastGrowth1yPct(), factors, score.getValuationRunId(),
                score.getValuationDataThrough(), score.getScoredAt());
    }

    /**
     * Stored JSON numbers come back without their scale (14.00 is read as 14); restores the 2 decimals the scoring
     * service returns, so a listed factor reads exactly like the live score of the same property.
     */
    private static OpportunityScoreDTO.Factor twoDecimals(OpportunityScoreDTO.Factor factor) {
        return new OpportunityScoreDTO.Factor(factor.code(), factor.label(), factor.category(), factor.weight(),
                factor.status(), scale(factor.signal()), factor.signalUnit(), scale(factor.subscore()),
                scale(factor.points()), factor.explanation());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
