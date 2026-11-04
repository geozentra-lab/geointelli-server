package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;

/** Current-user contact details; no authentication or permission state is exposed. */
public record CrmClientDTO(Long id, String name, String email, String phone, String notes,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
