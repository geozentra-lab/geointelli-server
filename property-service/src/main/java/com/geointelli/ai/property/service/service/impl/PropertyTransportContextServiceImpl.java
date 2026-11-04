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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * Reads the stored transportation analysis of the property through {@link TransportationAnalysisService}
 * (nothing is recalculated or saved, no second provider client) and turns its distance columns into
 * normalized {@link TransportCategory} options. A failure of the transport data source gives an
 * UNAVAILABLE section, so the other location intelligence sections can still be shown.
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
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }

        TransportContextDTO context = new TransportContextDTO();
        context.setPropertyId(propertyId);
        context.setDistanceUnit(TransportContextDTO.DISTANCE_UNIT);
        context.setSource(TransportContextDTO.SOURCE);
        context.setTransport(List.of());

        // reuse the existing transport integration (read only); a failure is returned, not thrown
        TransportationAnalysisDTO analysis;
        try {
            analysis = transportationAnalysisService.getPropertyTransportationAnalysisByIds(List.of(propertyId))
                    .stream().findFirst().orElse(null);
        } catch (RuntimeException e) {
            log.warn("Transport context unavailable for property {}", propertyId, e);
            context.setStatus(TransportContextDTO.STATUS_UNAVAILABLE);
            context.setError(UNAVAILABLE_ERROR);
            return context;
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

        context.setStatus(TransportContextDTO.STATUS_AVAILABLE);
        context.setTransport(transport);
        context.setBusStopsWithin500m(analysis.getBusStopsWithin500m());
        context.setTransportationScore(analysis.getTransportationScore());
        context.setDataUpdatedAt(analysis.getUpdatedAt());
        if (!unknown.isEmpty()) {
            context.setNote("Unknown distances: " + String.join(", ", unknown));
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
}
