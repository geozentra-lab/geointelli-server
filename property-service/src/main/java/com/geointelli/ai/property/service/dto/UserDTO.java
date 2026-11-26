package com.geointelli.ai.property.service.dto;

import lombok.Data;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;

@Data
public class UserDTO {
    private Long id;

    private String keycloakUserId;

    private String username;

    private String email;

    private String firstName;

    private String lastName;

    /** Status of the newest realtor license submission (SCRUM-200); null when none. */
    private RealtorLicenseVerificationStatus realtorLicenseStatus;
}
