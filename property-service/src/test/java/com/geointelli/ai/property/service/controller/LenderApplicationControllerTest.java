package com.geointelli.ai.property.service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.advice.LenderExceptionHandler;
import com.geointelli.ai.property.service.dto.*;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderApplicationImportService;
import com.geointelli.ai.property.service.service.LenderApplicationService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;

/** HTTP binding and safe errors, separate from actual database-backed workflow coverage. */
class LenderApplicationControllerTest {
    private final LenderApplicationService applications = mock(LenderApplicationService.class);
    private final LenderApplicationImportService imports = mock(LenderApplicationImportService.class);
    private final LenderOrganizationService organizations = mock(LenderOrganizationService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(7L);
        when(currentUser.getCurrentUserEntity()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new LenderApplicationController(applications, imports, currentUser),
                new LenderOrganizationController(organizations, currentUser))
            .setControllerAdvice(new LenderExceptionHandler(), new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper().registerModule(new JavaTimeModule())))
            .build();
    }

    private LenderApplicationDTO application() {
        return new LenderApplicationDTO(5L, 1L, 7L, "Applicant", null, null, new BigDecimal("100000.00"),
            "USD", LenderApplicationStage.DRAFT, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void createAndDetailsUseAuthenticatedActorAndExplicitOrganization() throws Exception {
        when(applications.create(eq(7L), eq(1L), any())).thenReturn(application());
        mvc.perform(post("/api/lender/applications?organizationId=1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"applicantName\":\"Applicant\",\"requestedAmount\":100000}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/lender/applications/5?organizationId=1"))
            .andExpect(jsonPath("$.id").value(5));
        when(applications.get(7L, 1L, 5L)).thenReturn(application());
        mvc.perform(get("/api/lender/applications/5?organizationId=1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.organizationId").value(1));
    }

    @Test
    void listBindsStageAndEmptyPagination() throws Exception {
        when(applications.list(7L, 1L, 2, 5, LenderApplicationStage.FUNDED))
            .thenReturn(new CrmPageDTO<>(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/lender/applications?organizationId=1&page=2&size=5&stage=FUNDED"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void patchBindsPresenceAndExplicitNullWithoutClearingOtherFields() throws Exception {
        when(applications.update(eq(7L), eq(1L), eq(5L), any())).thenReturn(application());
        mvc.perform(patch("/api/lender/applications/5?organizationId=1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":null,\"stage\":\"SUBMITTED\"}")).andExpect(status().isOk());
        verify(applications).update(eq(7L), eq(1L), eq(5L), argThat(request ->
            request.isNotesProvided() && request.getNotes() == null && request.isStageProvided()
                && request.getStage() == LenderApplicationStage.SUBMITTED && !request.isRequestedAmountProvided()));
    }

    @Test
    void importsBindSnapshotAndUseStaticRoute() throws Exception {
        when(imports.importApplications(eq(7L), eq(1L), any())).thenReturn(
            new LenderApplicationImportResult(1, 0, 0, 0, List.of(
                new LenderApplicationImportResult.RowResult(1, "CREATED", 5L, null))));
        mvc.perform(post("/api/lender/applications/import?organizationId=1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"PARTNER\",\"rows\":[{\"externalId\":\"A\",\"applicantName\":\"Applicant\","
                    + "\"requestedAmount\":100000,\"currency\":\"USD\",\"stage\":\"DRAFT\"}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1))
            .andExpect(jsonPath("$.rows[0].applicationId").value(5));
    }

    @Test
    void pipelineUsesItsOwnStaticRoute() throws Exception {
        mvc.perform(get("/api/lender/pipeline?organizationId=1")).andExpect(status().isOk());
        verify(applications).pipeline(7L, 1L);
    }

    @Test
    void organizationCreationMembershipAndListingRoutes() throws Exception {
        var organization = new LenderOrganizationDTO(1L, "Lender", 7L);
        when(organizations.create(7L, "Lender")).thenReturn(organization);
        mvc.perform(post("/api/lender/organizations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Lender\"}")).andExpect(status().isCreated())
            .andExpect(jsonPath("$.ownerUserId").value(7));
        when(organizations.list(7L)).thenReturn(List.of(organization));
        mvc.perform(get("/api/lender/organizations")).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        when(organizations.members(7L, 1L)).thenReturn(List.of(7L, 8L));
        mvc.perform(get("/api/lender/organizations/1/members")).andExpect(status().isOk()).andExpect(jsonPath("$[1]").value(8));
        mvc.perform(post("/api/lender/organizations/1/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":8}")).andExpect(status().isNoContent());
        mvc.perform(delete("/api/lender/organizations/1/members/8")).andExpect(status().isNoContent());
        verify(organizations).addMember(7L, 1L, 8L);
        verify(organizations).removeMember(7L, 1L, 8L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/lender/applications", "/api/lender/pipeline",
        "/api/lender/applications?organizationId=bad", "/api/lender/applications?organizationId=1&stage=BOGUS"})
    void missingOrInvalidScopeAndFiltersReturn400(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isBadRequest());
        verifyNoInteractions(applications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{\"stage\":\"BOGUS\"}", "{\"organizationId\":2}",
        "{\"approvedAt\":\"2020-01-01T00:00:00Z\"}", "{\"source\":\"OTHER\"}"})
    void unsupportedPatchFieldsAndInvalidJsonReturn400(String body) throws Exception {
        mvc.perform(patch("/api/lender/applications/5?organizationId=1").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(applications);
    }

    @Test
    void missingIdentityStopsBeforeTransactionalService() throws Exception {
        when(currentUser.getCurrentUserEntity()).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required"));
        mvc.perform(get("/api/lender/applications?organizationId=1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(applications);
    }

    @Test
    void safeNotFoundConflictAndInvalidInputResponses() throws Exception {
        when(applications.get(7L, 1L, 5L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        mvc.perform(get("/api/lender/applications/5?organizationId=1")).andExpect(status().isNotFound());
        when(applications.update(eq(7L), eq(1L), eq(5L), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported application transition"));
        mvc.perform(patch("/api/lender/applications/5?organizationId=1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"stage\":\"FUNDED\"}")).andExpect(status().isConflict());
        when(applications.create(eq(7L), eq(1L), any())).thenThrow(new IllegalArgumentException("requestedAmount is required"));
        mvc.perform(post("/api/lender/applications?organizationId=1").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentReferenceFailuresDoNotExposeSql() throws Exception {
        when(applications.create(eq(7L), eq(1L), any())).thenThrow(new DataIntegrityViolationException("private SQL details"));
        mvc.perform(post("/api/lender/applications?organizationId=1").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(
                "The lender record conflicts with an existing record or a reference changed; refresh and retry"));
    }
}

