package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.TransportContextDTO;
import com.geointelli.ai.property.service.dto.TransportOptionDTO;
import com.geointelli.ai.property.service.dto.TransportationAnalysisDTO;
import com.geointelli.ai.property.service.enums.TransportCategory;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertyTransportContextService;
import com.geointelli.ai.property.service.service.TransportationAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * Reads the stored transportation analysis of the property through {@link TransportationAnalysisService}
 * (nothing is saved, no second provider client) and turns its distance columns into normalized
 * {@link TransportCategory} options. The score is the transportation formula over the known part scores only,
 * so rows scored before unknown values were left out still get the right score. A failure of the transport
 * data source gives an UNAVAILABLE section, so the other location intelligence sections can still be shown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyTransportContextServiceImpl implements PropertyTransportContextService {

    static final String NO_COVERAGE_NOTE = "No transport data available for this property";
    static final String UNAVAILABLE_ERROR = "Transport data source unavailable";

    private final PropertyRepository propertyRepository;
    private final TransportationAnalysisService transportationAnalysisService;

    @Override
    public TransportContextDTO getTransportContext(Long propertyId) {
        // invalid ID -> 400, unknown property -> 404
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }

        TransportContextDTO context = new TransportContextDTO();
        context.setPropertyId(propertyId);
        context.setDistanceUnit(TransportContextDTO.DISTANCE_UNIT);
        context.setSource(TransportContextDTO.SOURCE);
        context.setTransport(List.of());

        // reuse the existing transport integration (read only); a data source failure is returned, not thrown
        TransportationAnalysisDTO analysis;
        try {
            analysis = transportationAnalysisService.getPropertyTransportationAnalysis(propertyId).orElse(null);
        } catch (DataAccessException e) {
            log.warn("Transport context unavailable for property {}", propertyId, e);
            context.setStatus(TransportContextDTO.STATUS_UNAVAILABLE);
            context.setError(UNAVAILABLE_ERROR);
            return context;
        }
        // an analysis always belongs to an existing property, so only check the property when there is none
        if (analysis == null && !propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }

        // no analysis, or an analysis without any value -> NO_COVERAGE
        if (analysis == null || (analysis.getDistMetroRail() == null
                && analysis.getDistMetroMover() == null
                && analysis.getDistBusStop() == null
                && analysis.getDistHighway() == null
                && analysis.getBusStopsWithin500m() == null)) {
            context.setStatus(TransportContextDTO.STATUS_NO_COVERAGE);
            context.setNote(NO_COVERAGE_NOTE);
            return context;
        }

        // normalize the distance columns into categories; unknown distances are listed, never set to 0
        List<TransportOptionDTO> transport = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        addOption(transport, unknown, TransportCategory.RAIL, analysis.getDistMetroRail());
        addOption(transport, unknown, TransportCategory.PEOPLE_MOVER, analysis.getDistMetroMover());
        addOption(transport, unknown, TransportCategory.BUS, analysis.getDistBusStop());
        addOption(transport, unknown, TransportCategory.HIGHWAY, analysis.getDistHighway());
        if (analysis.getBusStopsWithin500m() == null) {
            unknown.add("busStopsWithin500m");
        }

        // part scores only for known values, since older rows store unknown ones as 0
        Integer score = rounded(TransportationAnalysisServiceImpl.transportationScore(
                part(analysis.getDistMetroRail(), analysis.getMetroRailScore()),
                part(analysis.getDistMetroMover(), analysis.getMetroMoverScore()),
                analysis.getBusStopsWithin500m() == null ? null : toDouble(analysis.getBusStopDensityScore()),
                part(analysis.getDistBusStop(), analysis.getBusStopDistanceScore()),
                part(analysis.getDistHighway(), analysis.getHighwayScore())));

        context.setStatus(TransportContextDTO.STATUS_AVAILABLE);
        context.setTransport(transport);
        context.setBusStopsWithin500m(analysis.getBusStopsWithin500m());
        context.setTransportationScore(score);
        context.setTransportationScoreLabel(label(score));
        if (!unknown.isEmpty()) {
            context.setNote((score == null ? "Score unavailable" : "Score based on known values only")
                    + ", unknown values: " + String.join(", ", unknown));
        }
        return context;
    }

    private static void addOption(List<TransportOptionDTO> transport, List<String> unknown,
                                  TransportCategory category, BigDecimal distance) {
        if (distance == null) {
            unknown.add(category.name());
        } else {
            transport.add(new TransportOptionDTO(category, distance));
        }
    }

    private static Double part(BigDecimal rawValue, BigDecimal partScore) {
        return rawValue == null ? null : toDouble(partScore);
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private static Integer rounded(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    static String label(Integer score) {
        if (score == null) return null;
        if (score >= 80) return TransportContextDTO.LABEL_EXCELLENT;
        if (score >= 60) return TransportContextDTO.LABEL_GOOD;
        if (score >= 40) return TransportContextDTO.LABEL_FAIR;
        return TransportContextDTO.LABEL_LIMITED;
    }
}
