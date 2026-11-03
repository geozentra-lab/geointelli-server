package com.geointelli.ai.notification.service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.notification.service.config.NotificationJsonConfig;
import com.geointelli.ai.notification.service.config.NotificationSecurityConfig;
import com.geointelli.ai.notification.service.dto.NotificationDTO;
import com.geointelli.ai.notification.service.dto.NotificationPageDTO;
import com.geointelli.ai.notification.service.dto.NotificationReadRequest;
import com.geointelli.ai.notification.service.exception.NotificationApiExceptionHandler;
import com.geointelli.ai.notification.service.service.NotificationService;

/** Exercises request binding, JSON validation and the actual security filter chain without a broker or database. */
@WebMvcTest(NotificationController.class)
@Import({NotificationSecurityConfig.class, NotificationJsonConfig.class, NotificationApiExceptionHandler.class})
class NotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notifications;
    @MockitoBean JwtDecoder decoder;
    private final Instant time = Instant.parse("2026-10-30T01:38:04.720653Z");

    @BeforeEach
    void validBearer() {
        when(decoder.decode("valid-token")).thenReturn(Jwt.withTokenValue("valid-token")
            .header("alg", "RS256").subject("owner-a").build());
    }

    @Test
    void listsNotificationsWithDefaultPaginationAndReadStatus() throws Exception {
        when(notifications.list(0, 20)).thenReturn(new NotificationPageDTO(List.of(item(false)), 0, 20, 1, 1, true, true));

        mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.content[0].read").value(false))
            .andExpect(jsonPath("$.content[0].payload.newMatchCount").value(1))
            .andExpect(jsonPath("$.content[0].recipientKeycloakId").doesNotExist());
        verify(notifications).list(0, 20);
    }

    @Test
    void bindsExplicitPaginationWithoutAcceptingAnOwnerParameter() throws Exception {
        when(notifications.list(2, 5)).thenReturn(new NotificationPageDTO(List.of(), 2, 5, 0, 0, false, true));
        mvc.perform(get("/api/notifications?page=2&size=5&userId=other-user")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        verify(notifications).list(2, 5);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void emptyInboxHasZeroTotals() throws Exception {
        when(notifications.list(0, 20)).thenReturn(new NotificationPageDTO(List.of(), 0, 20, 0, 0, true, true));
        mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void forwardsInvalidPaginationAs400() throws Exception {
        when(notifications.list(-1, 20)).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination"));
        mvc.perform(get("/api/notifications?page=-1").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=abc", "size=abc", "page=2147483648"})
    void malformedPaginationDoesNotReachTheService(String query) throws Exception {
        mvc.perform(get("/api/notifications?"+query).header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(notifications);
    }

    @Test
    void marksReadAndReturnsTheSameTimestampOnRepeat() throws Exception {
        when(notifications.markRead(3L, new NotificationReadRequest(true))).thenReturn(item(true));
        for (int request = 0; request < 2; request++) {
            mvc.perform(patch("/api/notifications/3").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").value(time.toString()));
        }
        verify(notifications, times(2)).markRead(3L, new NotificationReadRequest(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"read\":false}", "{\"read\":null}", "{\"read\":\"true\"}",
        "{\"read\":1}", "{\"read\":true,\"recipientKeycloakId\":\"other-user\"}", "{invalid", "null"})
    void invalidUpdatesAreRejectedBeforeTheService(String body) throws Exception {
        mvc.perform(patch("/api/notifications/3").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(notifications);
    }

    @Test
    void missingBodyReturns400() throws Exception {
        mvc.perform(patch("/api/notifications/3").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(notifications);
    }

    @Test
    void nonNumericIdReturns400BeforeTheService() throws Exception {
        mvc.perform(patch("/api/notifications/abc").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(notifications);
    }

    @Test
    void missingOrForeignItemReturns404() throws Exception {
        when(notifications.markRead(50L, new NotificationReadRequest(true)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        mvc.perform(patch("/api/notifications/50").header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Notification not found"));
    }

    @Test
    void anonymousUsersCannotListOrUpdate() throws Exception {
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/notifications/3").contentType(MediaType.APPLICATION_JSON).content("{\"read\":true}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test
    void invalidBearerDoesNotReachTheService() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    private NotificationDTO item(boolean read) {
        return new NotificationDTO(3L, "NEW_MATCH", "New matching properties", "1 new match", 7L, 4L, null,
            new NotificationJsonConfig().notificationEventMapper().readTree("{\"newMatchCount\":1}"),
            time, time, read ? time : null, read);
    }
}
