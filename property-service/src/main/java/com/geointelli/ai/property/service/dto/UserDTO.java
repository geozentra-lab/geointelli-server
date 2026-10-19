package com.geointelli.ai.property.service.dto;

import lombok.Data;
import com.geointelli.ai.property.service.entity.UserPersona;

@Data
public class UserDTO {
    private Long id;

    private String keycloakUserId;

    private String username;

    private String email;

    private String firstName;

    private String lastName;

    private UserPersona persona;
}
