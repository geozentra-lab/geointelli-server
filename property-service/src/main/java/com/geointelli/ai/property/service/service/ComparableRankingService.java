package com.geointelli.ai.property.service.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.geointelli.ai.property.service.dto.ComparableCandidateDTO;
import com.geointelli.ai.property.service.dto.ComparableDTO;
import com.geointelli.ai.property.service.entity.Property;

/** Scores retrieved sales using documented, deterministic similarity rules. */
public interface ComparableRankingService {
    /**
     * Ranks all supplied candidates and explains awarded points and missing data.
     *
     * @param subject current subject property facts
     * @param candidates eligible recorded sale candidates
     * @param asOfDate date used for sale-age and build-year validation
     * @param radiusMeters distance scale used by the nearby search
     * @return ranked candidates, with stable tie-breaking
     */
    List<ComparableDTO> rank(Property subject, List<ComparableCandidateDTO> candidates,
            LocalDate asOfDate, int radiusMeters);

    /** Returns the version of the ranking rules used in responses. */
    String getVersion();

    /** Returns the maximum available points for each similarity rule. */
    Map<String, Integer> getWeights();
}
