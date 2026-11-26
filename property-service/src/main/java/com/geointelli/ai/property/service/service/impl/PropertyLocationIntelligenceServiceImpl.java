package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.dto.LocationIntelligenceDTO;
import com.geointelli.ai.property.service.dto.LocationIntelligenceDTO.SectionErrorDTO;
import com.geointelli.ai.property.service.dto.RiskContextDTO;
import com.geointelli.ai.property.service.dto.TransportContextDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.PropertyLocationIntelligenceService;
import com.geointelli.ai.property.service.service.PropertyRiskContextService;
import com.geointelli.ai.property.service.service.PropertySchoolContextService;
import com.geointelli.ai.property.service.service.PropertyTransportContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composes the location intelligence of a property from the school, transport and risk section services. The
 * property is checked once before any section is read, so an unknown ID is a 404 and never three failed sections.
 * This class is deliberately not transactional: each section service keeps its own read-only transaction, so a
 * section that fails cannot mark a shared transaction for rollback and break the sections read after it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyLocationIntelligenceServiceImpl implements PropertyLocationIntelligenceService {

    private static final int SECTION_COUNT = 3;

    private final PropertyRepository propertyRepository;
    private final PropertySchoolContextService schoolContextService;
    private final PropertyTransportContextService transportContextService;
    private final PropertyRiskContextService riskContextService;

    @Override
    public LocationIntelligenceDTO getLocationIntelligence(Long propertyId) {
        // invalid ID -> 400, unknown property -> 404, both before any section is read
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }

        LocationIntelligenceDTO result = new LocationIntelligenceDTO();
        result.setPropertyId(propertyId);
        List<SectionErrorDTO> errors = result.getErrors();

        // one section after the other; a failure is recorded and the next section is still read
        result.setSchools(read(LocationIntelligenceDTO.SECTION_SCHOOLS, propertyId, errors,
                () -> schoolContextService.getSchoolContext(propertyId)));

        TransportContextDTO transport = read(LocationIntelligenceDTO.SECTION_TRANSPORT, propertyId, errors,
                () -> transportContextService.getTransportContext(propertyId));
        if (transport != null && TransportContextDTO.STATUS_UNAVAILABLE.equals(transport.getStatus())) {
            errors.add(unavailable(LocationIntelligenceDTO.SECTION_TRANSPORT, transport.getError()));
        }
        result.setTransport(transport);

        RiskContextDTO risk = read(LocationIntelligenceDTO.SECTION_RISK, propertyId, errors,
                () -> riskContextService.getRiskContext(propertyId));
        if (risk != null && RiskContextDTO.STATUS_UNAVAILABLE.equals(risk.getStatus())) {
            errors.add(unavailable(LocationIntelligenceDTO.SECTION_RISK, risk.getError()));
        }
        result.setRisk(risk);

        if (errors.isEmpty()) {
            result.setStatus(LocationIntelligenceDTO.STATUS_COMPLETE);
        } else if (errors.size() == SECTION_COUNT) {
            result.setStatus(LocationIntelligenceDTO.STATUS_UNAVAILABLE);
        } else {
            result.setStatus(LocationIntelligenceDTO.STATUS_PARTIAL);
        }
        return result;
    }

    /**
     * Reads one section. Any failure except "property not found" is logged with its cause, recorded as
     * SECTION_FAILED with a message free of internals, and returned as null.
     */
    private <T> T read(String section, Long propertyId, List<SectionErrorDTO> errors, Supplier<T> reader) {
        try {
            return reader.get();
        } catch (PropertyNotFoundException e) {
            // the property was deleted while the sections were read: answer 404 like the section endpoints
            throw e;
        } catch (RuntimeException e) {
            log.warn("Location intelligence: {} section failed for property {}", section, propertyId, e);
            errors.add(new SectionErrorDTO(section, LocationIntelligenceDTO.ERROR_SECTION_FAILED,
                    "The " + section + " section could not be read"));
            return null;
        }
    }

    /** Error entry of a section that answered with its own UNAVAILABLE status. */
    private static SectionErrorDTO unavailable(String section, String sectionError) {
        String message = sectionError == null || sectionError.isBlank()
                ? "The " + section + " data source is unavailable" : sectionError;
        return new SectionErrorDTO(section, LocationIntelligenceDTO.ERROR_SECTION_UNAVAILABLE, message);
    }
}
