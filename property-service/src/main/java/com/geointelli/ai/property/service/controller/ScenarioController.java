package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.ScenarioCreateRequest;
import com.geointelli.ai.property.service.dto.ScenarioDTO;
import com.geointelli.ai.property.service.dto.ScenarioUpdateRequest;
import com.geointelli.ai.property.service.service.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** The current user's saved analysis scenarios (SCRUM-65 / SCRUM-147): assumptions only, no stored results. */
@RestController
@RequestMapping("/api/scenarios")
@RequiredArgsConstructor
@Tag(name = "Scenarios", description = "Bearer token identifies the owner; another user's scenario returns 404. "
        + "Stores user-supplied assumptions only, never property facts or calculation results. "
        + "page >= 0, size 1-100, newest first. Invalid input 400.")
@SecurityRequirement(name = "bearerAuth")
public class ScenarioController {

    private final ScenarioService scenarioService;

    /**
     * Saves a scenario for the current user.
     *
     * @param request name, type, optional property and assumptions
     * @return 201 with the scenario and its location; 400 if invalid; 401 without a token; 404 if the property is unknown
     */
    @PostMapping
    @Operation(summary = "Save a scenario", description = "Requires name, scenarioType (INVESTMENT) and assumptions, "
            + "a non-empty JSON object (for INVESTMENT, the body of POST /api/calculations/investment). propertyId is "
            + "optional and only linked. inputVersion is set by the server. Owner comes from the bearer token.")
    public ResponseEntity<ScenarioDTO> create(@Valid @RequestBody ScenarioCreateRequest request) {
        ScenarioDTO scenario = scenarioService.create(request);
        return ResponseEntity.created(URI.create("/api/scenarios/" + scenario.id())).body(scenario);
    }

    /**
     * Lists the current user's scenarios.
     *
     * @param page zero-based page number
     * @param size page size, between 1 and 100
     * @return 200 with one page, empty when the user has no scenario; 400 if page or size is invalid
     */
    @GetMapping
    @Operation(summary = "List my scenarios", description = "Zero-based page, size 1 to 100, newest ID first. "
            + "Only the current user's scenarios are returned.")
    public ResponseEntity<PageResponse<ScenarioDTO>> list(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(scenarioService.list(page, size));
    }

    /**
     * Returns one of the current user's scenarios.
     *
     * @param id scenario ID
     * @return 200 with the scenario; 400 if the ID is invalid; 404 if missing or owned by another user
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get one of my scenarios", description = "Missing or foreign IDs return 404.")
    public ResponseEntity<ScenarioDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(scenarioService.get(id));
    }

    /**
     * Changes the supplied fields of one of the current user's scenarios.
     *
     * @param id      scenario ID
     * @param request new name, property and/or assumptions
     * @return 200 with the updated scenario; 400 if nothing or something invalid is sent; 404 if missing or foreign
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Update one of my scenarios", description = "Only supplied fields change; at least one is "
            + "required. assumptions replaces the stored assumptions as a whole; it is not merged.")
    public ResponseEntity<ScenarioDTO> update(@PathVariable Long id, @Valid @RequestBody ScenarioUpdateRequest request) {
        return ResponseEntity.ok(scenarioService.update(id, request));
    }

    /**
     * Deletes one of the current user's scenarios.
     *
     * @param id scenario ID
     * @return 204; 404 if missing or owned by another user
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of my scenarios", description = "Deletes only the saved scenario; the linked "
            + "property and every other record are unchanged. Returns 204; missing or foreign IDs return 404.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scenarioService.delete(id);
        return ResponseEntity.noContent().build();
    }


}
