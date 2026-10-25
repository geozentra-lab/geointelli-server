package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.dto.SavedSearchCreate;
import com.geointelli.ai.property.service.dto.SavedSearchDTO;
import com.geointelli.ai.property.service.dto.SavedSearchPageDTO;
import com.geointelli.ai.property.service.dto.SavedSearchRequest;
import com.geointelli.ai.property.service.service.SavedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** User-owned saved definitions and manual replay, independent of role/persona permissions. */
@RestController
@RequestMapping("/api/saved-searches")
@Tag(name = "Saved searches", description = "Bearer token identifies the owner. Monitoring settings do not yet schedule alerts.")
@SecurityRequirement(name = "bearerAuth")
public class SavedSearchController {
    private final SavedSearchService searches;

    public SavedSearchController(SavedSearchService searches) {
        this.searches = searches;
    }

    @PostMapping
    @Operation(summary = "Save a named property search", description = "Requires name and filters. Uses property-search "
        + "validation without executing the query. Stores versioned geography, property filters and sorting, "
        + "not results or page/size. Defaults: DAILY cadence, paused=false. Owner is resolved from the bearer token.")
    public ResponseEntity<SavedSearchDTO> create(@Validated(SavedSearchCreate.class) @RequestBody SavedSearchRequest request) {
        SavedSearchDTO result = searches.create(request);
        return ResponseEntity.created(URI.create("/api/saved-searches/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my saved searches", description = "Zero-based page, size 1 to 100, newest ID first.")
    public SavedSearchPageDTO list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searches.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an owned search definition", description = "Returns stored filters and monitoring settings. "
        + "Missing or foreign IDs return 404; missing bearer identity returns 401.")
    public SavedSearchDTO get(@PathVariable Long id) {
        return searches.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename, edit, pause or resume a search", description = "Only supplied fields change. "
        + "A filters object replaces the entire saved filter definition; it is not merged. "
        + "Explicit null values are rejected. Pausing affects future monitoring only; manual replay remains available.")
    public SavedSearchDTO update(@PathVariable Long id, @Valid @RequestBody SavedSearchRequest request) {
        return searches.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a saved search", description = "Permanently deletes the definition and its monitoring "
        + "settings. Does not delete any property or result data. Returns 204; missing/foreign IDs return 404.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        searches.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/results")
    @Operation(summary = "Replay a saved search", description = "Runs the saved filters/sorting through property search "
        + "against current data, even when paused. page/size are supplied here (defaults 0/20, max size 100). "
        + "The query definition is reproduced; historical results are not frozen. "
        + "Unsupported or invalid stored filter versions return 409 instead of silently changing the query.")
    public PropertySearchResponse results(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searches.results(id, page, size);
    }
}
