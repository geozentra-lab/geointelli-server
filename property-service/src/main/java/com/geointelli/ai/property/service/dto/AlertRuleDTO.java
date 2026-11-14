package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.geointelli.ai.property.service.entity.ValuationChangeDirection;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEventType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Exposes configured rules without claiming that monitoring or notification
 * delivery is running.
 */
@Schema(description = "Configuration only. effectivePaused is paused OR the linked saved search's paused setting. "
        + "Cadence belongs to this rule after creation; later changes to the search's cadence do not overwrite it. "
        + "LISTING_PRICE_CHANGED requires actual listing data; assessed value is not a listing price.")
public record AlertRuleDTO(Long id, String name, AlertEventType eventType, Long propertyId, Long savedSearchId,
        AlertCadence cadence, boolean paused, boolean effectivePaused,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime updatedAt,
        BigDecimal minimumChangePercent, ValuationChangeDirection changeDirection) {
    /** Retains the existing Java construction contract for non-valuation rules. */
    public AlertRuleDTO(Long id, String name, AlertEventType eventType, Long propertyId, Long savedSearchId,
            AlertCadence cadence, boolean paused, boolean effectivePaused, LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this(id, name, eventType, propertyId, savedSearchId, cadence, paused, effectivePaused, createdAt, updatedAt,
                null, null);
    }
}
