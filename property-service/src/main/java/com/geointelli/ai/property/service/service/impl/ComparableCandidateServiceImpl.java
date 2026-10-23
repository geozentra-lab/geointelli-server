package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableCandidateRequest;
import com.geointelli.ai.property.service.dto.ComparableCandidateResponse;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.ComparableCandidateProjection;
import com.geointelli.ai.property.service.repository.ComparableCandidateRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.ComparableCandidateService;

/** Selects conservative sale candidates using current property characteristics. */
@Service
public class ComparableCandidateServiceImpl implements ComparableCandidateService {
    private static final int MINIMUM_CANDIDATES = 3;

    private final PropertyRepository propertyRepository;
    private final ComparableCandidateRepository comparableCandidateRepository;

    public ComparableCandidateServiceImpl(PropertyRepository propertyRepository,
            ComparableCandidateRepository comparableCandidateRepository) {
        this.propertyRepository = propertyRepository;
        this.comparableCandidateRepository = comparableCandidateRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ComparableCandidateResponse findCandidates(Long propertyId, ComparableCandidateRequest request) {
        validate(propertyId, request);
        Property subject = propertyRepository.findById(propertyId)
            .orElseThrow(() -> new PropertyNotFoundException("Property not found: " + propertyId));

        LocalDate asOfDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate dateFrom = asOfDate.minusMonths(request.getMonths());
        List<String> warnings = new ArrayList<>();
        List<ComparableCandidateDTO> candidates = new ArrayList<>();
        warnings.add("Qualification uses Q plus an explicit allowlist of observed qualification descriptions. "
            + "Unknown descriptions, conflicting qualifications and known multi-parcel transactions are excluded.");
        warnings.add("Sale provider and ingestion freshness are not recorded in the sales schema; "
            + "sale dates are transaction dates, not source refresh dates.");
        warnings.add("Similarity uses current property facts, which may differ from facts at the sale date.");
        warnings.add("A shared parent identifies a related property group, not a verified same-building match.");

        boolean canSearch = true;
        if (subject.getCounty() == null) {
            warnings.add("The subject has no county; candidates cannot be safely matched.");
            canSearch = false;
        }
        if (subject.getDorCode() == null || subject.getDorCode().isBlank()) {
            warnings.add("The subject has no property-type code; candidates cannot be safely matched.");
            canSearch = false;
        }
        if (subject.getBuildingHeatedArea() == null || subject.getBuildingHeatedArea().signum() <= 0) {
            warnings.add("The subject has no positive heated area; the area similarity filter is unavailable.");
        }
        if (subject.getBedroomCount() == null) {
            warnings.add("The subject has no bedroom count; the bedroom similarity filter is unavailable.");
        }

        if (canSearch) {
            Long parentId = subject.getParentProperty() == null ? null : subject.getParentProperty().getId();
            if (!comparableCandidateRepository.hasGeometry(propertyId, parentId)) {
                warnings.add("The subject has no usable parcel geometry; only parent-group/community matches are available.");
            }
            List<ComparableCandidateProjection> rows = comparableCandidateRepository.findCandidates(propertyId,
                dateFrom, asOfDate, request.getRadiusMeters(), request.getAreaTolerancePercent(),
                request.getBedroomTolerance(), request.getLimit());
            for (ComparableCandidateProjection row : rows) {
                candidates.add(toDTO(row));
            }
        }
        if (candidates.size() < MINIMUM_CANDIDATES) {
            warnings.add("Fewer than three eligible properties were found. Do not treat this as a supported valuation.");
        }
        return ComparableCandidateResponse.builder()
            .propertyId(propertyId).asOfDate(asOfDate).saleDateFrom(dateFrom)
            .radiusMeters(request.getRadiusMeters()).areaTolerancePercent(request.getAreaTolerancePercent())
            .bedroomTolerance(request.getBedroomTolerance()).limit(request.getLimit())
            .minimumCandidates(MINIMUM_CANDIDATES).returnedCount(candidates.size())
            .status(candidates.size() < MINIMUM_CANDIDATES ? "INSUFFICIENT_DATA" : "CANDIDATES_FOUND")
            .selectionVersion("qualified-sales-v1").warnings(warnings).candidates(candidates).build();
    }

    private ComparableCandidateDTO toDTO(ComparableCandidateProjection row) {
        return ComparableCandidateDTO.builder()
            .propertyId(row.getPropertyId()).folio(row.getFolio()).countyId(row.getCountyId())
            .address(row.getAddress()).city(row.getCity()).zip(row.getZip()).dorCode(row.getDorCode())
            .bedrooms(row.getBedrooms()).bathrooms(row.getBathrooms()).livingAreaSqft(row.getLivingAreaSqft())
            .yearBuilt(row.getYearBuilt()).saleId(row.getSaleId()).saleDate(row.getSaleDate())
            .salePrice(row.getSalePrice()).qualifiedFlag(row.getQualifiedFlag())
            .qualificationDescription(row.getQualificationDescription()).instrumentNumber(row.getInstrumentNumber())
            .matchLevel(row.getMatchLevel()).distanceMeters(row.getDistanceMeters()).build();
    }

    private void validate(Long propertyId, ComparableCandidateRequest request) {
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("propertyId must be positive");
        }
        if (request == null || request.getMonths() < 1 || request.getMonths() > 120
                || request.getRadiusMeters() < 100 || request.getRadiusMeters() > 10000
                || request.getAreaTolerancePercent() < 0 || request.getAreaTolerancePercent() > 100
                || request.getBedroomTolerance() < 0 || request.getBedroomTolerance() > 5
                || request.getLimit() < 3 || request.getLimit() > 100) {
            throw new IllegalArgumentException("Require months 1-120, radiusMeters 100-10000, "
                + "areaTolerancePercent 0-100, bedroomTolerance 0-5 and limit 3-100");
        }
    }
}
