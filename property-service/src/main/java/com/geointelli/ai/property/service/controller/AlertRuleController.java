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
import com.geointelli.ai.property.service.dto.AlertRuleCreate;
import com.geointelli.ai.property.service.dto.AlertRuleDTO;
import com.geointelli.ai.property.service.dto.AlertRulePageDTO;
import com.geointelli.ai.property.service.dto.AlertRuleRequest;
import com.geointelli.ai.property.service.service.AlertRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Configures alert rules for the bearer-token user; no background evaluation or messages are started here. */
@RestController
@RequestMapping("/api/alerts")
@Tag(name = "Alert rules", description = "Configuration only. NEW_MATCH: newly matching property in a saved search. "
    + "LISTING_PRICE_CHANGED: actual listing price changes. VALUATION_CHANGED: supported valuation changes. "
    + "Evaluation and queue delivery are implemented separately; missing data is not substituted with tax/assessment values.")
@SecurityRequirement(name = "bearerAuth")
public class AlertRuleController {
    private final AlertRuleService rules;

    public AlertRuleController(AlertRuleService rules) {
        this.rules = rules;
    }

    @PostMapping
    @Operation(summary = "Configure an alert rule", description = "Requires name, eventType and exactly one internal propertyId "
        + "or owned savedSearchId. NEW_MATCH requires savedSearchId; other types accept either target. "
        + "One rule per owner/target/event type; duplicates return 409. Cadence defaults to the saved search's current cadence, "
        + "or DAILY for a property; paused defaults to false. The cadence is then stored independently on the rule. "
        + "The linked search's paused setting also pauses its rules.")
    public ResponseEntity<AlertRuleDTO> create(@Validated(AlertRuleCreate.class) @RequestBody AlertRuleRequest request) {
        AlertRuleDTO result = rules.create(request);
        return ResponseEntity.created(URI.create("/api/alerts/" + result.id())).body(result);
    }

    @GetMapping
    @Operation(summary = "List my alert rules", description = "Zero-based page; size 1 to 100, default 20. "
        + "Sorted by descending ID. Empty data stays empty.")
    public AlertRulePageDTO list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return rules.list(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one alert rule", description = "Missing and foreign rules return 404. "
        + "effectivePaused reflects the rule pause and the linked saved search pause. Missing bearer identity returns 401.")
    public AlertRuleDTO get(@PathVariable Long id) {
        return rules.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Rename, reschedule, pause or resume a rule", description = "Changes supplied name, cadence and paused "
        + "fields only; omitted fields remain unchanged and explicit null for these fields is invalid. "
        + "Target and eventType are immutable; delete/recreate to change them, keeping future evaluation identity unambiguous. "
        + "Resuming a rule does not override a paused saved search.")
    public AlertRuleDTO update(@PathVariable Long id, @Valid @RequestBody AlertRuleRequest request) {
        return rules.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an alert rule", description = "Permanently removes only this rule, preserving the "
        + "property and saved search. Returns 204; missing/foreign rules return 404. "
        + "Deleting a target property or saved search also removes its linked rules.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        rules.delete(id);
        return ResponseEntity.noContent().build();
    }
}
