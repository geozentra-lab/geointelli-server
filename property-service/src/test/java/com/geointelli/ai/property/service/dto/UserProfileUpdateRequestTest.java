package com.geointelli.ai.property.service.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/** PATCH /api/users/me body: the persona is ignored since SCRUM-204, other unknown fields still fail. */
class UserProfileUpdateRequestTest {

    private final ObjectMapper strict = new ObjectMapper();

    @Test
    void personaFromAnOlderClientIsIgnored() throws Exception {
        var request = strict.readValue("{\"firstName\":\"Ann\",\"persona\":\"REALTOR\"}", UserProfileUpdateRequest.class);
        assertThat(request.getFirstName()).isEqualTo("Ann");
    }

    @Test
    void otherUnknownFieldsStillFail() {
        assertThatThrownBy(() -> strict.readValue("{\"role\":\"lender\"}", UserProfileUpdateRequest.class))
            .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
