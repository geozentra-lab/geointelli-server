package com.geointelli.ai.property.service.mapper;

import org.springframework.stereotype.Component;
import com.geointelli.ai.property.service.dto.LenderApplicationDTO;
import com.geointelli.ai.property.service.entity.LenderApplication;

/** Maps persisted tracking data without deriving credit decisions. */
@Component
public class LenderApplicationMapper {
    public LenderApplicationDTO toDTO(LenderApplication value) {
        return new LenderApplicationDTO(value.getId(), value.getOrganizationId(), value.getCreatedByUserId(),
            value.getApplicantName(), value.getApplicantEmail(), value.getPropertyId(), value.getRequestedAmount(),
            value.getCurrency(), value.getStage(), value.getApprovedAmount(), value.getFundedAmount(),
            value.getApprovedAt(), value.getFundedAt(), value.getNotes(), value.getSource(), value.getExternalId(),
            value.getCreatedAt(), value.getUpdatedAt());
    }
}

