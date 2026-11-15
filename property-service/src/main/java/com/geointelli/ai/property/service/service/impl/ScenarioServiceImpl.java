package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ScenarioCreateRequest;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.dto.ScenarioUpdateRequest;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.Scenario;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.mapper.ScenarioMapper;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.ScenarioRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.ScenarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owner-scoped scenario storage. Not read-only: resolving the current user creates their row on a first login,
 * as in the saved searches.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class ScenarioServiceImpl implements ScenarioService {


    static final int INPUT_VERSION = 1;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_ASSUMPTIONS_LENGTH = 10_000;

    private final ScenarioRepository scenarioRepository;
    private final PropertyRepository propertyRepository;
    private final CurrentUserService currentUserService;
    private final ScenarioMapper scenarioMapper;

    @Override
    public ScenarioDTO create(ScenarioCreateRequest request) {
        User owner = currentUserService.getCurrentUserEntity();
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        requireName(request.name());
        if (request.scenarioType() == null) {
            throw new IllegalArgumentException("scenarioType is required");
        }
        requireAssumptions(request.assumptions());

        Scenario scenario = new Scenario();
        scenario.setUser(owner);
        scenario.setName(request.name().trim());
        scenario.setScenarioType(request.scenarioType());
        scenario.setInputVersion(INPUT_VERSION);
        scenario.setAssumptions(request.assumptions());
        if (request.propertyId() != null) {
            scenario.setProperty(linkedProperty(request.propertyId()));
        }
        scenarioRepository.saveAndFlush(scenario);
        return scenarioMapper.toDTO(scenario);
    }

    @Override
    public PageResponse<ScenarioDTO> list(int page, int size) {
        Long ownerId = currentUserService.getCurrentUserEntity().getId();
        if (page < 0) {
            throw new IllegalArgumentException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageResponse.from(scenarioRepository
                .findByUser_Id(ownerId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
                .map(scenarioMapper::toDTO));
    }

    @Override
    public ScenarioDTO get(Long id) {
        Scenario scenario = owned(id);
        return scenarioMapper.toDTO(scenario);
    }

    @Override
    public ScenarioDTO update(Long id, ScenarioUpdateRequest request) {
        Scenario scenario = owned(id);
        if (request == null || (request.name() == null && request.propertyId() == null && request.assumptions() == null)) {
            throw new IllegalArgumentException("At least one of name, propertyId or assumptions is required");
        }
        if (request.name() != null) {
            requireName(request.name());
            scenario.setName(request.name().trim());
        }
        if (request.propertyId() != null) {
            scenario.setProperty(linkedProperty(request.propertyId()));
        }
        if (request.assumptions() != null) {
            requireAssumptions(request.assumptions());
            scenario.setAssumptions(request.assumptions());
            scenario.setInputVersion(INPUT_VERSION);
        }
        scenarioRepository.saveAndFlush(scenario);
        return scenarioMapper.toDTO(scenario);
    }

    @Override
    public void delete(Long id) {
        // Only the scenario row is removed; the linked property is never touched
        Scenario scenario = owned(id);
        scenarioRepository.delete(scenario);
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.trim().length() > 120) {
            throw new IllegalArgumentException("name must be at most 120 characters");
        }
    }

    private static void requireAssumptions(JsonNode assumptions) {
        if (assumptions == null || !assumptions.isObject() || assumptions.isEmpty()) {
            throw new IllegalArgumentException("assumptions must be a non-empty JSON object");
        }
        if (assumptions.toString().length() > MAX_ASSUMPTIONS_LENGTH) {
            throw new IllegalArgumentException("assumptions must be at most " + MAX_ASSUMPTIONS_LENGTH + " characters");
        }
    }

    private Property linkedProperty(Long propertyId) {
        if (propertyId <= 0) {
            throw new IllegalArgumentException("propertyId must be a positive number");
        }
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property found with id: " + propertyId);
        }
        // A reference only: the property row is linked, never loaded or changed
        return propertyRepository.getReferenceById(propertyId);
    }

    /** The scenario of the current user; a missing or foreign scenario is the same 404. */
    private Scenario owned(Long id) {
        Long ownerId = currentUserService.getCurrentUserEntity().getId();
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("id must be a positive number");
        }
        return scenarioRepository.findByIdAndUser_Id(id, ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scenario not found"));
    }
}
