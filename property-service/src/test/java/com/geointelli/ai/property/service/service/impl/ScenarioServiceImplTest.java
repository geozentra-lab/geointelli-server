package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ScenarioCreateRequest;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.dto.ScenarioUpdateRequest;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.Scenario;
import com.geointelli.ai.property.service.entity.ScenarioType;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.mapper.ScenarioMapper;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.ScenarioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class ScenarioServiceImplTest {

    ScenarioRepository scenarioRepository = mock(ScenarioRepository.class);
    PropertyRepository propertyRepository = mock(PropertyRepository.class);
    CurrentUserService currentUserService = mock(CurrentUserService.class);
    ScenarioMapper scenarioMapper = Mappers.getMapper(ScenarioMapper.class);

    ScenarioServiceImpl scenarioService = new ScenarioServiceImpl(
            scenarioRepository, propertyRepository, currentUserService, scenarioMapper);

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    private User owner;

    private static JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static JsonNode assumptions() {
        return json("{\"operating\": {\"monthlyRent\": 2500}}");
    }

    private Scenario stored(Long id) {
        Scenario scenario = new Scenario();
        scenario.setId(id);
        scenario.setUser(owner);
        scenario.setName("Miami duplex");
        scenario.setScenarioType(ScenarioType.INVESTMENT);
        scenario.setAssumptions(assumptions());
        return scenario;
    }

    @BeforeEach
    void currentUserIsOwner() {
        owner = new User();
        owner.setId(OWNER_ID);
        when(currentUserService.getCurrentUserEntity()).thenReturn(owner);
    }

    @Nested
    @DisplayName("Create Tests")
    class Create {

        @Test
        @DisplayName("When the request is valid, then save it for the current user with input version 1")
        void whenRequestIsValid_thenSaveForCurrentUser() {
            // Arrange
            Property property = new Property();
            property.setId(42L);
            when(propertyRepository.existsById(42L)).thenReturn(true);
            when(propertyRepository.getReferenceById(42L)).thenReturn(property);
            ArgumentCaptor<Scenario> saved = ArgumentCaptor.forClass(Scenario.class);

            // Act
            ScenarioDTO result = scenarioService.create(new ScenarioCreateRequest(
                    "  Miami duplex  ", ScenarioType.INVESTMENT, 42L, assumptions()));

            // Assert
            verify(scenarioRepository).saveAndFlush(saved.capture());
            Scenario scenario = saved.getValue();
            assertSame(owner, scenario.getUser());
            assertEquals("Miami duplex", scenario.getName());
            assertEquals(ScenarioType.INVESTMENT, scenario.getScenarioType());
            assertEquals(ScenarioServiceImpl.INPUT_VERSION, scenario.getInputVersion());
            assertSame(property, scenario.getProperty());
            assertEquals(assumptions(), scenario.getAssumptions());
            assertEquals("Miami duplex", result.name());
            assertEquals(42L, result.propertyId());
            assertEquals(1, result.inputVersion());
        }

        @Test
        @DisplayName("When no property is linked, then save the scenario without property")
        void whenNoProperty_thenSaveWithoutProperty() {
            // Act
            ScenarioDTO result = scenarioService.create(new ScenarioCreateRequest(
                    "Miami duplex", ScenarioType.INVESTMENT, null, assumptions()));

            // Assert
            assertNull(result.propertyId());
            verifyNoInteractions(propertyRepository);
        }

        @Test
        @DisplayName("When the linked property does not exist, then throw PropertyNotFoundException without saving")
        void whenPropertyUnknown_thenThrowPropertyNotFoundException() {
            // Arrange
            when(propertyRepository.existsById(999L)).thenReturn(false);

            // Act & Assert
            PropertyNotFoundException exception = assertThrows(PropertyNotFoundException.class,
                    () -> scenarioService.create(new ScenarioCreateRequest(
                            "Miami duplex", ScenarioType.INVESTMENT, 999L, assumptions())));
            assertEquals("No property found with id: 999", exception.getMessage());
            verify(scenarioRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("When the name, type or assumptions are invalid, then throw IllegalArgumentException without saving")
        void whenInputInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> scenarioService.create(null));
            assertThrows(IllegalArgumentException.class, () -> scenarioService.create(
                    new ScenarioCreateRequest("   ", ScenarioType.INVESTMENT, null, assumptions())));
            assertThrows(IllegalArgumentException.class, () -> scenarioService.create(
                    new ScenarioCreateRequest("Miami duplex", null, null, assumptions())));
            for (String value : List.of("[1, 2]", "\"text\"", "{}")) {
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                        () -> scenarioService.create(new ScenarioCreateRequest(
                                "Miami duplex", ScenarioType.INVESTMENT, null, json(value))));
                assertEquals("assumptions must be a non-empty JSON object", exception.getMessage());
            }
            assertThrows(IllegalArgumentException.class, () -> scenarioService.create(new ScenarioCreateRequest(
                    "Miami duplex", ScenarioType.INVESTMENT, null,
                    json("{\"note\": \"" + "x".repeat(ScenarioServiceImpl.MAX_ASSUMPTIONS_LENGTH) + "\"}"))));
            verify(scenarioRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("List Tests")
    class ListScenarios {

        @Test
        @DisplayName("When listing, then read only the current user's scenarios, newest first")
        void whenListing_thenReadOwnScenariosNewestFirst() {
            // Arrange
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            when(scenarioRepository.findByUser_Id(eq(OWNER_ID), pageable.capture()))
                    .thenAnswer(call -> new PageImpl<>(List.of(stored(8L), stored(7L)), call.getArgument(1), 2));

            // Act
            PageResponse<ScenarioDTO> page = scenarioService.list(1, 2);

            // Assert
            assertEquals(List.of(8L, 7L), page.content().stream().map(ScenarioDTO::id).toList());
            assertEquals(1, pageable.getValue().getPageNumber());
            assertEquals(2, pageable.getValue().getPageSize());
            assertEquals(Sort.by(Sort.Direction.DESC, "id"), pageable.getValue().getSort());
        }

        @Test
        @DisplayName("When the user has no scenario, then return an empty page")
        void whenNoScenario_thenReturnEmptyPage() {
            // Arrange
            when(scenarioRepository.findByUser_Id(eq(OWNER_ID), any()))
                    .thenAnswer(call -> new PageImpl<>(List.of(), call.getArgument(1), 0));

            // Act
            PageResponse<ScenarioDTO> page = scenarioService.list(0, 20);

            // Assert
            assertTrue(page.content().isEmpty());
            assertEquals(0, page.totalElements());
        }

        @Test
        @DisplayName("When page or size is out of range, then throw IllegalArgumentException")
        void whenPageOrSizeInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertEquals("page must be 0 or greater",
                    assertThrows(IllegalArgumentException.class, () -> scenarioService.list(-1, 20)).getMessage());
            assertEquals("size must be between 1 and 100",
                    assertThrows(IllegalArgumentException.class, () -> scenarioService.list(0, 0)).getMessage());
            assertThrows(IllegalArgumentException.class, () -> scenarioService.list(0, 101));
            verifyNoInteractions(scenarioRepository);
        }
    }

    @Nested
    @DisplayName("Get Tests")
    class Get {

        @Test
        @DisplayName("When the scenario belongs to the current user, then return it")
        void whenOwned_thenReturnScenario() {
            // Arrange
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.of(stored(7L)));

            // Act
            ScenarioDTO result = scenarioService.get(7L);

            // Assert
            assertEquals(7L, result.id());
            assertEquals(assumptions(), result.assumptions());
        }

        @Test
        @DisplayName("When the scenario is missing or belongs to another user, then return 404")
        void whenMissingOrForeign_thenThrowNotFound() {
            // Arrange: the owner-scoped lookup finds nothing for a foreign scenario
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> scenarioService.get(7L));
            assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
            assertEquals("Scenario not found", exception.getReason());
            verify(scenarioRepository, never()).findById(any());
            verify(scenarioRepository, never()).findByIdAndUser_Id(7L, OTHER_USER_ID);
        }

        @Test
        @DisplayName("When the ID is 0 or null, then throw IllegalArgumentException")
        void whenIdInvalid_thenThrowIllegalArgumentException() {
            // Act & Assert
            assertThrows(IllegalArgumentException.class, () -> scenarioService.get(0L));
            assertThrows(IllegalArgumentException.class, () -> scenarioService.get(null));
            verifyNoInteractions(scenarioRepository);
        }
    }

    @Nested
    @DisplayName("Update Tests")
    class Update {

        @Test
        @DisplayName("When only the name is sent, then change only the name")
        void whenOnlyName_thenChangeOnlyName() {
            // Arrange
            Scenario scenario = stored(7L);
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.of(scenario));

            // Act
            ScenarioDTO result = scenarioService.update(7L, new ScenarioUpdateRequest(" New name ", null, null));

            // Assert
            assertEquals("New name", result.name());
            assertEquals(assumptions(), result.assumptions());
            verify(scenarioRepository).saveAndFlush(scenario);
            verifyNoInteractions(propertyRepository);
        }

        @Test
        @DisplayName("When assumptions are sent, then replace them as a whole")
        void whenAssumptions_thenReplaceAsWhole() {
            // Arrange
            Scenario scenario = stored(7L);
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.of(scenario));
            JsonNode replacement = json("{\"operating\": {\"monthlyRent\": 2200}}");

            // Act
            ScenarioDTO result = scenarioService.update(7L, new ScenarioUpdateRequest(null, null, replacement));

            // Assert
            assertEquals(replacement, result.assumptions());
            assertEquals("Miami duplex", result.name());
            assertEquals(ScenarioServiceImpl.INPUT_VERSION, result.inputVersion());
        }

        @Test
        @DisplayName("When nothing or something invalid is sent, then throw IllegalArgumentException without saving")
        void whenNothingOrInvalid_thenThrowIllegalArgumentException() {
            // Arrange
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.of(stored(7L)));

            // Act & Assert
            assertEquals("At least one of name, propertyId or assumptions is required",
                    assertThrows(IllegalArgumentException.class,
                            () -> scenarioService.update(7L, new ScenarioUpdateRequest(null, null, null))).getMessage());
            assertThrows(IllegalArgumentException.class,
                    () -> scenarioService.update(7L, new ScenarioUpdateRequest("  ", null, null)));
            assertThrows(IllegalArgumentException.class,
                    () -> scenarioService.update(7L, new ScenarioUpdateRequest(null, null, json("[]"))));
            verify(scenarioRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("When the scenario belongs to another user, then return 404 without saving")
        void whenForeign_thenThrowNotFound() {
            // Arrange
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResponseStatusException.class,
                    () -> scenarioService.update(7L, new ScenarioUpdateRequest("New name", null, null)));
            verify(scenarioRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("Delete Tests")
    class Delete {

        @Test
        @DisplayName("When the scenario belongs to the current user, then delete only that scenario")
        void whenOwned_thenDeleteOnlyScenario() {
            // Arrange
            Scenario scenario = stored(7L);
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.of(scenario));

            // Act
            scenarioService.delete(7L);

            // Assert
            verify(scenarioRepository).delete(scenario);
            verifyNoInteractions(propertyRepository);
        }

        @Test
        @DisplayName("When the scenario belongs to another user, then return 404 and delete nothing")
        void whenForeign_thenThrowNotFoundAndDeleteNothing() {
            // Arrange
            when(scenarioRepository.findByIdAndUser_Id(7L, OWNER_ID)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ResponseStatusException.class, () -> scenarioService.delete(7L));
            verify(scenarioRepository, never()).delete(any());
            verify(scenarioRepository, never()).deleteById(any());
        }
    }
}
