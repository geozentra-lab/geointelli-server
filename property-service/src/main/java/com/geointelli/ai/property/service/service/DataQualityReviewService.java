package com.geointelli.ai.property.service.service;

import java.util.Collection;
import java.util.Map;
import com.geointelli.ai.property.service.dto.DataQualityIssueReviewDTO;
import com.geointelli.ai.property.service.dto.DataQualityReviewEventDTO;
import com.geointelli.ai.property.service.dto.DataQualityReviewRequest;

/**
 * Review and resolution of data-quality findings for administrators (SCRUM-190). Findings are the ones the
 * scanner of SCRUM-189 reports, identified by their reference {@code RULE:entityType:entityId}. A review only
 * appends to the review log; the source records are never changed.
 */
public interface DataQualityReviewService {

    /**
     * Moves a finding to a new review status and records who, when and why.
     *
     * @param reference finding reference, for example {@code SALE_DUPLICATE:SALE:9012}
     * @param request   target status and reason
     * @return the finding's review state after the move, with its full history
     * @throws IllegalArgumentException if the reference is malformed, the rule is unknown, the entity type
     *                                  does not match the rule, or the reason is missing when required (400)
     * @throws com.geointelli.ai.property.service.exception.DataQualityEntityNotFoundException
     *                                  if the entity does not exist (404)
     * @throws org.springframework.web.server.ResponseStatusException
     *                                  with 409 if the move is not a supported transition from the current status
     */
    DataQualityIssueReviewDTO review(String reference, DataQualityReviewRequest request);

    /**
     * Current review state and history of a finding. A finding that was never reviewed is OPEN with an
     * empty history.
     *
     * @throws IllegalArgumentException if the reference is malformed (400)
     * @throws com.geointelli.ai.property.service.exception.DataQualityEntityNotFoundException
     *                                  if the entity does not exist (404)
     */
    DataQualityIssueReviewDTO get(String reference);

    /**
     * Latest review action of each given reference, in one query; references never reviewed are absent from
     * the map. Used to decorate a page of the scanner report.
     */
    Map<String, DataQualityReviewEventDTO> latestByReference(Collection<String> references);
}
