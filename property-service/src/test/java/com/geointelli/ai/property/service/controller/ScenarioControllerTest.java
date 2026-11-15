package com.geointelli.ai.property.service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.entity.ScenarioType;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.ScenarioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScenarioController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ScenarioControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ScenarioService scenarioService;

    private static final String URL = "/api/scenarios";
    private static final String BODY = """
            {"name": "Miami duplex", "scenarioType": "INVESTMENT", "propertyId": 42,
             "assumptions": {"operating": {"monthlyRent": 2500}}}""";

    private static ScenarioDTO scenario(Long id) throws Exception {
        return new ScenarioDTO(id, "Miami duplex", ScenarioType.INVESTMENT, 1, 42L,
                new ObjectMapper().readTree("{\"operating\": {\"monthlyRent\": 2500}}"),
                LocalDateTime.of(2026, 11, 10, 10, 0), LocalDateTime.of(2026, 11, 10, 10, 0));
    }

    @Nested
    @DisplayName("Test Create Scenario Endpoint")
    class CreateScenarioTest {

        @Test
        @DisplayName("When the body is valid, then return 201 with the location and the stored assumptions")
        void whenBodyValid_thenReturn201() throws Exception {
            // Arrange
            when(scenarioService.create(any())).thenReturn(scenario(7L));

            // Act & Assert
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/scenarios/7"))
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.scenarioType").value("INVESTMENT"))
                    .andExpect(jsonPath("$.inputVersion").value(1))
                    .andExpect(jsonPath("$.propertyId").value(42))
                    .andExpect(jsonPath("$.assumptions.operating.monthlyRent").value(2500))
                    .andExpect(jsonPath("$.createdAt").value("2026-11-10T10:00:00"));
        }

        @ParameterizedTest
        @DisplayName("When the body is invalid, then return 400 without calling the service")
        @ValueSource(strings = {
                "{}",
                // name blank / too long
                """
                        {"name": " ", "scenarioType": "INVESTMENT", "assumptions": {"a": 1}}""",
                // type missing / unknown
                """
                        {"name": "A", "assumptions": {"a": 1}}""",
                """
                        {"name": "A", "scenarioType": "MORTGAGE", "assumptions": {"a": 1}}""",
                // assumptions missing
                """
                        {"name": "A", "scenarioType": "INVESTMENT"}""",
                // property id not positive
                """
                        {"name": "A", "scenarioType": "INVESTMENT", "propertyId": 0, "assumptions": {"a": 1}}""",
                "{ not json"
        })
        void whenBodyInvalid_thenReturn400(String body) throws Exception {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(scenarioService);
        }

        @Test
        @DisplayName("When the linked property does not exist, then return 404")
        void whenPropertyUnknown_thenReturn404() throws Exception {
            // Arrange
            when(scenarioService.create(any())).thenThrow(new PropertyNotFoundException("No property found with id: 42"));

            // Act & Assert
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("No property found with id: 42"));
        }
    }

    @Nested
    @DisplayName("Test List Scenarios Endpoint")
    class ListScenariosTest {

        @Test
        @DisplayName("When no parameter is given, then use page 0 and size 20")
        void whenNoParameter_thenUseDefaults() throws Exception {
            // Arrange
            when(scenarioService.list(0, 20)).thenReturn(
                    new PageResponse<>(List.of(scenario(7L)), 0, 20, 1, 1, true, true));

            // Act & Assert
            mockMvc.perform(get(URL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(7))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("When size is out of range, then return 400")
        void whenSizeInvalid_thenReturn400() throws Exception {
            // Arrange
            when(scenarioService.list(0, 101)).thenThrow(new IllegalArgumentException("size must be between 1 and 100"));

            // Act & Assert
            mockMvc.perform(get(URL).param("size", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("size must be between 1 and 100"));
        }
    }

    @Nested
    @DisplayName("Test Get, Update and Delete Scenario Endpoints")
    class ScenarioByIdTest {

        @Test
        @DisplayName("When the scenario is owned, then GET returns 200")
        void whenOwned_thenGetReturns200() throws Exception {
            // Arrange
            when(scenarioService.get(7L)).thenReturn(scenario(7L));

            // Act & Assert
            mockMvc.perform(get(URL + "/7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Miami duplex"));
        }

        @Test
        @DisplayName("When the scenario is missing or foreign, then GET, PATCH and DELETE return 404")
        void whenMissingOrForeign_thenReturn404() throws Exception {
            // Arrange
            ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found");
            when(scenarioService.get(7L)).thenThrow(notFound);
            when(scenarioService.update(eq(7L), any())).thenThrow(notFound);
            doThrow(notFound).when(scenarioService).delete(7L);

            // Act & Assert
            mockMvc.perform(get(URL + "/7")).andExpect(status().isNotFound());
            mockMvc.perform(patch(URL + "/7").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"B\"}"))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete(URL + "/7")).andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("When the ID is not a number, then return 400 without calling the service")
        void whenIdNotANumber_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(get(URL + "/abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(scenarioService);
        }

        @Test
        @DisplayName("When PATCH is valid, then return 200 with the updated scenario")
        void whenPatchValid_thenReturn200() throws Exception {
            // Arrange
            when(scenarioService.update(eq(7L), any())).thenReturn(scenario(7L));

            // Act & Assert
            mockMvc.perform(patch(URL + "/7").contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Miami duplex\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7));
        }

        @Test
        @DisplayName("When PATCH has a too long name, then return 400 without calling the service")
        void whenPatchNameTooLong_thenReturn400() throws Exception {
            // Act & Assert
            mockMvc.perform(patch(URL + "/7").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\": \"" + "x".repeat(121) + "\"}"))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(scenarioService);
        }

        @Test
        @DisplayName("When DELETE succeeds, then return 204")
        void whenDeleteSucceeds_thenReturn204() throws Exception {
            // Act & Assert
            mockMvc.perform(delete(URL + "/7")).andExpect(status().isNoContent());
            verify(scenarioService).delete(7L);
        }
    }
}
