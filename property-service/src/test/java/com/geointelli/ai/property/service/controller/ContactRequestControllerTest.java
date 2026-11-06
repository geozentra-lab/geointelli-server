package com.geointelli.ai.property.service.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.ContactRequestExceptionHandler;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.dto.ContactResponseDTO;
import com.geointelli.ai.property.service.dto.ContactRequestSubmissionDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.ContactRequestService;
import com.geointelli.ai.property.service.service.CurrentUserService;

class ContactRequestControllerTest {
    private final ContactRequestService contacts = mock(ContactRequestService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;
    @BeforeEach void setup() {
        User user = new User(); user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new ContactRequestController(contacts, currentUser))
            .setControllerAdvice(new ContactRequestExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule())))
            .build();
    }
    private ContactResponseDTO result() {
        return new ContactResponseDTO(1L, 100L, 8L, 2L, "SUBMITTED", "PENDING", Instant.parse("2020-01-01T00:00:00Z"),
            "Person", "person@example.com", null, "Arrange a viewing", "property-contact-v1");
    }
    @Test void createsAndReplaysWithAuthenticatedOwnerAndHeader() throws Exception {
        when(contacts.submit(eq(7L), any(), eq("request-1"))).thenReturn(new ContactRequestSubmissionDTO(result(), true),
            new ContactRequestSubmissionDTO(result(), false));
        for (int status : new int[] {201, 200}) {
            mvc.perform(post("/api/contact-requests").header("Idempotency-Key", "request-1")
                .contentType(MediaType.APPLICATION_JSON).content("{\"propertyId\":100,\"professionalUserId\":8}"))
                .andExpect(status().is(status)).andExpect(jsonPath("$.deliveryStatus").value("PENDING"));
        }
        verify(contacts, times(2)).submit(eq(7L), argThat(r -> r.professionalUserId().equals(8L)), eq("request-1"));
    }
    @Test void listsProfessionalsAndReadsOwnedStatus() throws Exception {
        when(contacts.professionals(0, 20)).thenReturn(new CrmPageDTO<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/api/contact-requests/professionals")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        when(contacts.get(7L, 1L)).thenReturn(result());
        mvc.perform(get("/api/contact-requests/1")).andExpect(status().isOk()).andExpect(jsonPath("$.professionalUserId").value(8));
    }
    @ParameterizedTest @ValueSource(strings={"{", "{\"requesterUserId\":8}", "{\"consent\":{}}"})
    void malformedOrForgedBodyCannotReachService(String body) throws Exception {
        mvc.perform(post("/api/contact-requests").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(contacts);
    }
    @Test void missingIdentityStopsBeforeSubmissionTransaction() throws Exception {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required"));
        mvc.perform(post("/api/contact-requests").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(contacts);
    }
    @Test void ownershipValidationAndIdempotencyErrorsKeepTheirStatus() throws Exception {
        when(contacts.get(7L, 1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact request not found"));
        mvc.perform(get("/api/contact-requests/1")).andExpect(status().isNotFound());
        when(contacts.submit(eq(7L), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Key already used"));
        mvc.perform(post("/api/contact-requests").header("Idempotency-Key","same").contentType(MediaType.APPLICATION_JSON)
            .content("{}")).andExpect(status().isConflict());
    }
}

