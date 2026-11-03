package com.geointelli.ai.notification.service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.config.NotificationSecurityConfig;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesDTO;
import com.geointelli.ai.notification.service.entity.EmailCadence;
import com.geointelli.ai.notification.service.exception.NotificationApiExceptionHandler;
import com.geointelli.ai.notification.service.service.NotificationPreferenceService;

@WebMvcTest(NotificationPreferenceController.class)
@Import({ NotificationJsonConfig.class, NotificationSecurityConfig.class, NotificationApiExceptionHandler.class })
class NotificationPreferenceControllerTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    NotificationPreferenceService preferences;
    @MockitoBean
    JwtDecoder decoder;
    private static final String VALID = "{\"inAppEnabled\":true,\"emailEnabled\":false,\"enabledEventTypes\":[\"NEW_MATCH\"],\"emailCadence\":\"DAILY\"}";
    private final NotificationPreferencesDTO defaults = new NotificationPreferencesDTO(true, false, Set.of("NEW_MATCH"),
            EmailCadence.DAILY, null, null, null, false);

    @BeforeEach
    void jwt() {
        when(decoder.decode("valid"))
                .thenReturn(Jwt.withTokenValue("valid").header("alg", "RS256").subject("owner").build());
    }

    @Test
    void readsDocumentedDefaults() throws Exception {
        when(preferences.get()).thenReturn(defaults);
        mvc.perform(get("/api/notifications/preferences").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inAppEnabled").value(true))
                .andExpect(jsonPath("$.emailEnabled").value(false)).andExpect(jsonPath("$.emailCadence").value("DAILY"))
                .andExpect(jsonPath("$.emailDeliveryEnabled").value(false));
    }

    @Test
    void replacesPreferencesWithoutAcceptingAnOwner() throws Exception {
        when(preferences.update(any())).thenReturn(defaults);
        mvc.perform(put("/api/notifications/preferences").header("Authorization", "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isOk());
        verify(preferences).update(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "{}", "null", "{bad",
            "{\"inAppEnabled\":true,\"emailEnabled\":false,\"enabledEventTypes\":[null],\"emailCadence\":\"DAILY\"}",
            "{\"inAppEnabled\":true,\"emailEnabled\":false,\"enabledEventTypes\":[\"UNKNOWN\"],\"emailCadence\":\"DAILY\"}",
            "{\"inAppEnabled\":true,\"emailEnabled\":false,\"enabledEventTypes\":[],\"emailCadence\":\"HOURLY\"}",
            "{\"inAppEnabled\":true,\"emailEnabled\":false,\"enabledEventTypes\":[],\"emailCadence\":\"DAILY\",\"emailAddress\":\"other@example.com\"}" })
    void invalidValuesAndRecipientInjectionAreRejected(String body) throws Exception {
        mvc.perform(put("/api/notifications/preferences").header("Authorization", "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(preferences);
    }

    @Test
    void missingAuthenticationCannotReadOrChangePreferences() throws Exception {
        mvc.perform(get("/api/notifications/preferences")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/notifications/preferences").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(preferences);
    }

    @Test
    void unverifiedEmailErrorIsReturnedAs400() throws Exception {
        when(preferences.update(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verified email required"));
        mvc.perform(put("/api/notifications/preferences").header("Authorization", "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content(VALID)).andExpect(status().isBadRequest());
    }
}
