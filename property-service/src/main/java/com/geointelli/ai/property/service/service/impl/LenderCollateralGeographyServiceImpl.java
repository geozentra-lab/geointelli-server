package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO;
import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO.Geography;
import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO.Location;
import com.geointelli.ai.property.service.dto.LenderCollateralAnalysisDTO.RiskSource;
import com.geointelli.ai.property.service.dto.RiskContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.LenderCollateralGeographyService;
import com.geointelli.ai.property.service.service.PropertyRiskContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads the location of the property and reuses {@link PropertyRiskContextService} (SCRUM-127) unchanged. The
 * status only says which sources have evidence: no rating is combined or added. Not transactional: the risk
 * context runs each source query on its own, so one unreadable source only affects its own section.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LenderCollateralGeographyServiceImpl implements LenderCollateralGeographyService {

    /** Section statuses that mean the source does not cover the location or cannot be read. */
    private static final Set<String> MISSING = Set.of(RiskContextDTO.STATUS_NO_COVERAGE,
            RiskContextDTO.STATUS_UNAVAILABLE);

    private final PropertyRepository properties;
    private final PropertyRiskContextService riskContexts;

    @Override
    public Geography describe(Long propertyId) {
        Location location = properties.findCollateralLocation(propertyId)
                .map(view -> new Location(view.getFolio(), view.getAddress(), view.getCity(), view.getZip(),
                        view.getCountyName(), view.getStateCode()))
                .orElse(null);
        if (location == null) {
            return Geography.unavailable(LenderCollateralAnalysisDTO.COLLATERAL_PROPERTY_NOT_FOUND, null);
        }

        // The risk section of SCRUM-127, unchanged; an error must not hide the collateral value and the LTV
        RiskContextDTO risk;
        try {
            risk = riskContexts.getRiskContext(propertyId);
        } catch (PropertyNotFoundException e) {
            return Geography.unavailable(LenderCollateralAnalysisDTO.COLLATERAL_PROPERTY_NOT_FOUND, location);
        } catch (RuntimeException e) {
            log.warn("Risk context of property {} unavailable for the collateral analysis", propertyId, e);
            return Geography.unavailable(LenderCollateralAnalysisDTO.RISK_CONTEXT_UNAVAILABLE, location);
        }
        if (risk == null || RiskContextDTO.STATUS_UNAVAILABLE.equals(risk.getStatus())) {
            return new Geography(LenderCollateralAnalysisDTO.GEOGRAPHY_UNAVAILABLE,
                    List.of(LenderCollateralAnalysisDTO.RISK_CONTEXT_UNAVAILABLE), location, List.of(), List.of(),
                    risk);
        }
        if (RiskContextDTO.STATUS_NO_COVERAGE.equals(risk.getStatus())) {
            return new Geography(LenderCollateralAnalysisDTO.GEOGRAPHY_NO_COVERAGE,
                    List.of(LenderCollateralAnalysisDTO.NO_PARCEL_POLYGON), location, List.of(),
                    List.of(LenderCollateralAnalysisDTO.SECTION_FLOOD, LenderCollateralAnalysisDTO.SECTION_HAZARDS,
                            LenderCollateralAnalysisDTO.SECTION_STORMS),
                    risk);
        }

        // One source per section, as the risk context reports it
        List<RiskSource> sources = new ArrayList<>();
        RiskContextDTO.FloodZoneDTO flood = risk.getFlood();
        sources.add(flood == null ? missing(LenderCollateralAnalysisDTO.SECTION_FLOOD, RiskContextDTO.SOURCE_FLOOD)
                : new RiskSource(LenderCollateralAnalysisDTO.SECTION_FLOOD, flood.getSource(), flood.getStatus(),
                flood.getDataAsOf(), flood.getNote()));
        RiskContextDTO.HazardRatingsDTO hazards = risk.getHazards();
        sources.add(hazards == null ? missing(LenderCollateralAnalysisDTO.SECTION_HAZARDS, RiskContextDTO.SOURCE_HAZARDS)
                : new RiskSource(LenderCollateralAnalysisDTO.SECTION_HAZARDS, hazards.getSource(), hazards.getStatus(),
                hazards.getDataAsOf(), hazards.getNote()));
        RiskContextDTO.StormHistoryDTO storms = risk.getStorms();
        sources.add(storms == null ? missing(LenderCollateralAnalysisDTO.SECTION_STORMS, RiskContextDTO.SOURCE_STORMS)
                : new RiskSource(LenderCollateralAnalysisDTO.SECTION_STORMS, storms.getSource(), storms.getStatus(),
                storms.getDataAsOf(), storms.getNote()));

        List<String> missingCoverage = sources.stream()
                .filter(source -> source.status() == null || MISSING.contains(source.status()))
                .map(RiskSource::section)
                .toList();
        String status;
        if (missingCoverage.isEmpty()) {
            status = LenderCollateralAnalysisDTO.GEOGRAPHY_AVAILABLE;
        } else if (missingCoverage.size() < sources.size()) {
            status = LenderCollateralAnalysisDTO.GEOGRAPHY_PARTIAL;
        } else {
            status = LenderCollateralAnalysisDTO.GEOGRAPHY_NO_COVERAGE;
        }
        return new Geography(status, List.of(), location, sources, missingCoverage, risk);
    }

    /** A section the risk context did not return. */
    private static RiskSource missing(String section, String source) {
        return new RiskSource(section, source, RiskContextDTO.STATUS_UNAVAILABLE, null, null);
    }
}
