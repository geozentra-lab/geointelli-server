package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ScenarioCreateRequest;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.dto.ScenarioUpdateRequest;

/**
 * Saves and manages the current user's analysis scenarios (SCRUM-147). Every operation is scoped to the user
 * of the bearer token: another user's scenario behaves as if it did not exist. Only user-supplied assumptions are
 * stored; property facts and calculation results are never written.
 */
public interface ScenarioService {

    /**
     * Saves a new scenario for the current user, with input version 1.
     *
     * @param request name, type, optional linked property and assumptions (a non-empty JSON object)
     * @return the saved scenario
     * @throws IllegalArgumentException if a field is missing or invalid
     * @throws com.geointelli.ai.property.service.exception.PropertyNotFoundException if the linked property does not exist
     */
    ScenarioDTO create(ScenarioCreateRequest request);

    /**
     * Lists the current user's scenarios, newest first.
     *
     * @param page zero-based page number, 0 or greater
     * @param size page size, between 1 and 100
     * @return one page of the user's scenarios; empty when the user has none
     * @throws IllegalArgumentException if page or size is out of range
     */
    PageResponse<ScenarioDTO> list(int page, int size);

    /**
     * Returns one of the current user's scenarios.
     *
     * @param id scenario ID
     * @return the scenario
     * @throws IllegalArgumentException if the ID is not positive
     * @throws org.springframework.web.server.ResponseStatusException 404 if the scenario is missing or belongs to another user
     */
    ScenarioDTO get(Long id);

    /**
     * Changes only the supplied fields of one of the current user's scenarios; assumptions are replaced as a whole.
     *
     * @param id      scenario ID
     * @param request new name, linked property and/or assumptions; at least one is required
     * @return the updated scenario
     * @throws IllegalArgumentException if no field is supplied or a supplied field is invalid
     * @throws org.springframework.web.server.ResponseStatusException 404 if the scenario is missing or belongs to another user
     */
    ScenarioDTO update(Long id, ScenarioUpdateRequest request);

    /**
     * Deletes one of the current user's scenarios; the linked property and everything else stay unchanged.
     *
     * @param id scenario ID
     * @throws IllegalArgumentException if the ID is not positive
     * @throws org.springframework.web.server.ResponseStatusException 404 if the scenario is missing or belongs to another user
     */
    void delete(Long id);
}
