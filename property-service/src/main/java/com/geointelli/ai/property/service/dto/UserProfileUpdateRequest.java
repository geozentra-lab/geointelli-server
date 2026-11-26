package com.geointelli.ai.property.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Name changes of PATCH /api/users/me. The account type is a realm role, chosen on the onboarding page (SCRUM-204);
 * a {@code persona} sent by an older client is ignored instead of failing the whole update.
 */
@JsonIgnoreProperties({"persona"})
@Data
public class UserProfileUpdateRequest {
    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String firstName;

    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String lastName;
}
