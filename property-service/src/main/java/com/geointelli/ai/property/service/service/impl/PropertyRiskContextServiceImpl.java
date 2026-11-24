package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.RiskContextDTO;
import com.geointelli.ai.property.service.dto.RiskContextDTO.FloodZoneDTO;
import com.geointelli.ai.property.service.dto.RiskContextDTO.HazardRatingDTO;
import com.geointelli.ai.property.service.dto.RiskContextDTO.HazardRatingsDTO;
import com.geointelli.ai.property.service.dto.RiskContextDTO.StormHistoryDTO;
import com.geointelli.ai.property.service.dto.RiskContextDTO.StormTypeCountDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.RiskReferenceReader;
import com.geointelli.ai.property.service.repository.RiskReferenceReader.FloodZoneRow;
import com.geointelli.ai.property.service.repository.RiskReferenceReader.HazardRow;
import com.geointelli.ai.property.service.repository.RiskReferenceReader.NriRow;
import com.geointelli.ai.property.service.repository.RiskReferenceReader.ParcelPoint;
import com.geointelli.ai.property.service.repository.RiskReferenceReader.StormTypeRow;
import com.geointelli.ai.property.service.repository.RiskReferenceReader.TractRow;
import com.geointelli.ai.property.service.service.PropertyRiskContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the risk section from the SCRUM-201 reference tables through {@link RiskReferenceReader}. Every
 * block is filled from raw source values: no score, label or default is derived here, and a block without
 * evidence says so in its status and note. One read-only transaction per call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyRiskContextServiceImpl implements PropertyRiskContextService {

    static final String NO_COVERAGE_NOTE = "No parcel polygon for this property: the location cannot be evaluated";
    static final String NO_COUNTY_NOTE = "The property's county has no FIPS code; county-based sources cannot be evaluated";
    static final String UNAVAILABLE_ERROR = "Environmental reference data unavailable";
    static final String FLOOD_NOT_LOADED_NOTE = "FEMA flood map of this county not loaded";
    static final String FLOOD_NO_POLYGON_NOTE = "No FEMA flood zone polygon at this point; this is not a flood-free determination";
    static final String FLOOD_OVERLAP_NOTE = "Point lies in %d overlapping FEMA polygons; the Special Flood Hazard Area one is shown";
    static final String TRACT_NOT_FOUND_NOTE = "No census tract polygon contains this point";
    static final String NRI_NO_ROW_NOTE = "FEMA publishes no NRI row for this tract (typically water-only tracts)";
    static final String RATING_UNKNOWN_NOTE = "Ratings left null are not published by FEMA for this tract (insufficient data), not low risk";
    static final String STORMS_NOT_LOADED_NOTE = "NOAA storm events of this state not loaded";
    static final String STORMS_NONE_NOTE = "No located NOAA event within the radius; county-level events may still exist";
    static final String STORMS_PARTIAL_NOTE = "Only events with coordinates are counted within the radius; county-wide events include those without";

    private static final Map<String, String> HAZARD_NAMES = Map.of(
            "HRCN", "Hurricane", "CFLD", "Coastal flooding", "IFLD", "Inland (riverine) flooding",
            "TRND", "Tornado", "WFIR", "Wildfire", "SWND", "Strong wind", "LTNG", "Lightning", "HWAV", "Heat wave");

    private final PropertyRepository propertyRepository;
    private final RiskReferenceReader reader;

    @Override
    @Transactional(readOnly = true)
    public RiskContextDTO getRiskContext(Long propertyId) {
        // invalid ID -> 400, unknown property -> 404
        if (propertyId == null || propertyId <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }

        RiskContextDTO context = new RiskContextDTO();
        context.setPropertyId(propertyId);
        context.setDistanceUnit(RiskContextDTO.DISTANCE_UNIT);

        // the reference tables are read only; a failure is returned in the section, not thrown
        try {
            Optional<ParcelPoint> located = reader.parcelPoint(propertyId);
            if (located.isEmpty()) {
                context.setStatus(RiskContextDTO.STATUS_NO_COVERAGE);
                context.setNote(NO_COVERAGE_NOTE);
                return context;
            }
            ParcelPoint point = located.get();
            context.setFlood(flood(point));
            context.setHazards(hazards(point));
            context.setStorms(storms(point));
            context.setStatus(RiskContextDTO.STATUS_AVAILABLE);
            if (point.fips5() == null) {
                context.setNote(NO_COUNTY_NOTE);
            }
        } catch (DataAccessException e) {
            log.warn("Risk context unavailable for property {}", propertyId, e);
            context.setStatus(RiskContextDTO.STATUS_UNAVAILABLE);
            context.setError(UNAVAILABLE_ERROR);
            context.setFlood(null);
            context.setHazards(null);
            context.setStorms(null);
        }
        return context;
    }

    /** FEMA flood zone at the point: the county's map must be loaded, then a polygon must contain the point. */
    private FloodZoneDTO flood(ParcelPoint point) {
        FloodZoneDTO flood = new FloodZoneDTO();
        flood.setSource(RiskContextDTO.SOURCE_FLOOD);
        if (point.fips5() == null || !reader.floodZonesLoaded(point.fips5())) {
            flood.setStatus(RiskContextDTO.STATUS_NO_COVERAGE);
            flood.setNote(FLOOD_NOT_LOADED_NOTE);
            return flood;
        }
        flood.setDataAsOf(reader.loadedOn(RiskReferenceReader.DATASET_FLOOD_ZONES, point.fips5()).orElse(null));
        List<FloodZoneRow> zones = reader.floodZonesAt(point.fips5(), point.lon(), point.lat());
        if (zones.isEmpty()) {
            flood.setStatus(RiskContextDTO.STATUS_NO_DATA);
            flood.setNote(FLOOD_NO_POLYGON_NOTE);
            return flood;
        }
        FloodZoneRow zone = zones.get(0); // SFHA polygons sort first
        flood.setStatus(RiskContextDTO.STATUS_AVAILABLE);
        flood.setZone(zone.zone());
        flood.setZoneSubtype(zone.zoneSubtype());
        flood.setSpecialFloodHazardArea(zone.sfha());
        flood.setBaseFloodElevationFt(zone.staticBfe());
        flood.setDepthFt(zone.depth());
        flood.setStudyType(zone.studyType());
        flood.setFirmId(zone.firmId());
        if (zones.size() > 1) {
            flood.setNote(FLOOD_OVERLAP_NOTE.formatted(zones.size()));
        }
        return flood;
    }

    /** NRI ratings of the tract containing the point; a tract without an NRI row is NO_DATA, not low risk. */
    private HazardRatingsDTO hazards(ParcelPoint point) {
        HazardRatingsDTO hazards = new HazardRatingsDTO();
        hazards.setSource(RiskContextDTO.SOURCE_HAZARDS);
        hazards.setDataAsOf(reader.loadedOn(RiskReferenceReader.DATASET_NRI_TRACTS, null).orElse(null));
        Optional<TractRow> tract = reader.tractAt(point.lon(), point.lat());
        if (tract.isEmpty()) {
            hazards.setStatus(RiskContextDTO.STATUS_NO_COVERAGE);
            hazards.setNote(TRACT_NOT_FOUND_NOTE);
            return hazards;
        }
        hazards.setTractFips(tract.get().tractFips());
        hazards.setTractName(tract.get().name());
        Optional<NriRow> nri = reader.nri(tract.get().tractFips());
        if (nri.isEmpty()) {
            hazards.setStatus(RiskContextDTO.STATUS_NO_DATA);
            hazards.setNote(NRI_NO_ROW_NOTE);
            return hazards;
        }
        NriRow row = nri.get();
        hazards.setStatus(RiskContextDTO.STATUS_AVAILABLE);
        hazards.setNriVersion(row.version());
        hazards.setOverallScore(row.riskScore());
        hazards.setOverallRating(row.riskRating());
        List<HazardRatingDTO> ratings = new ArrayList<>(row.hazards().size());
        boolean unknown = row.riskRating() == null;
        for (HazardRow hazard : row.hazards()) {
            HazardRatingDTO rating = new HazardRatingDTO();
            rating.setCode(hazard.code());
            rating.setName(HAZARD_NAMES.getOrDefault(hazard.code(), hazard.code()));
            rating.setScore(hazard.score());
            rating.setRating(hazard.rating());
            ratings.add(rating);
            unknown |= hazard.rating() == null;
        }
        hazards.setRatings(ratings);
        if (unknown) {
            hazards.setNote(RATING_UNKNOWN_NOTE);
        }
        return hazards;
    }

    /** NOAA events near the point and in the county since the fixed year; counts only, no rating. */
    private StormHistoryDTO storms(ParcelPoint point) {
        StormHistoryDTO storms = new StormHistoryDTO();
        storms.setSource(RiskContextDTO.SOURCE_STORMS);
        storms.setRadius(RiskContextDTO.STORM_RADIUS_M);
        storms.setFromYear(RiskContextDTO.STORM_FROM_YEAR);
        if (point.fips5() == null || !reader.stormEventsLoaded(point.fips5())) {
            storms.setStatus(RiskContextDTO.STATUS_NO_COVERAGE);
            storms.setNote(STORMS_NOT_LOADED_NOTE);
            return storms;
        }
        storms.setDataAsOf(reader.loadedOn(RiskReferenceReader.DATASET_STORM_EVENTS, null).orElse(null));
        List<StormTypeRow> near = reader.stormsNear(point.lon(), point.lat(),
                RiskContextDTO.STORM_RADIUS_M, RiskContextDTO.STORM_FROM_YEAR);
        storms.setEventsInCounty(reader.stormsInCounty(point.fips5(), RiskContextDTO.STORM_FROM_YEAR));
        List<StormTypeCountDTO> byType = new ArrayList<>(near.size());
        int total = 0;
        for (StormTypeRow row : near) {
            StormTypeCountDTO type = new StormTypeCountDTO();
            type.setEventType(row.eventType());
            type.setCount(row.count());
            type.setLastEventDate(row.lastEventDate());
            type.setPropertyDamageUsd(row.propertyDamageUsd());
            byType.add(type);
            total += row.count();
            if (row.lastEventDate() != null
                    && (storms.getLastEventDate() == null || row.lastEventDate().isAfter(storms.getLastEventDate()))) {
                storms.setLastEventDate(row.lastEventDate());
            }
        }
        storms.setByType(byType);
        storms.setEventsWithinRadius(total);
        if (total == 0) {
            storms.setStatus(RiskContextDTO.STATUS_NO_DATA);
            storms.setNote(STORMS_NONE_NOTE);
        } else {
            storms.setStatus(RiskContextDTO.STATUS_AVAILABLE);
            storms.setNote(STORMS_PARTIAL_NOTE);
        }
        return storms;
    }
}
