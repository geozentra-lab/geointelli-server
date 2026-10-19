package com.geointelli.ai.property.service.dto;

import com.geointelli.ai.property.service.entity.UserPersona;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String firstName;

    @Size(max = 100)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    private String lastName;

    private UserPersona persona;
}
