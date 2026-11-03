package com.geointelli.ai.property.service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmNoteRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.service.CrmActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Reads real CRM history and accepts notes only for accessible records. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "CRM activity", description = "Append-only owned history, newest ID first, page >=0 and size 1-100. "
    + "System events are created transactionally; callers can submit notes only. Activity timestamps are UTC instants. "
    + "No activity is invented from page visits, and reading never creates an activity.")
public class CrmActivityController {
    private final CrmActivityService activities;

    @GetMapping("/crm/activities")
    @Operation(summary = "Read my CRM activity", description = "Includes retained deletion events. Historical subject "
        + "IDs may refer to a deleted client; empty history is a normal empty page.")
    public CrmPageDTO<CrmActivityDTO> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return activities.list(page, size);
    }

    @GetMapping("/clients/{id}/activities")
    @Operation(summary = "Read a client's history", description = "Includes activities for the client and its leads. "
        + "Missing, deleted and foreign clients return 404.")
    public CrmPageDTO<CrmActivityDTO> clientHistory(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return activities.clientHistory(id, page, size);
    }

    @GetMapping("/leads/{id}/activities")
    @Operation(summary = "Read a lead's history", description = "Missing/foreign leads return 404; empty history returns 200.")
    public CrmPageDTO<CrmActivityDTO> leadHistory(@PathVariable Long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return activities.leadHistory(id, page, size);
    }

    @PostMapping("/clients/{id}/activities")
    @Operation(summary = "Add a client note", description = "Body: description (1-2000 characters). "
        + "Event type, owner and timestamp are set by the server. Returns 201; this is a new note on every submission.")
    public ResponseEntity<CrmActivityDTO> clientNote(@PathVariable Long id, @RequestBody CrmNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activities.addClientNote(id, request));
    }

    @PostMapping("/leads/{id}/activities")
    @Operation(summary = "Add a lead note", description = "Body: description (1-2000 characters). "
        + "The note is also included in the client's history. Returns 201; missing/foreign lead is 404.")
    public ResponseEntity<CrmActivityDTO> leadNote(@PathVariable Long id, @RequestBody CrmNoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(activities.addLeadNote(id, request));
    }
}
