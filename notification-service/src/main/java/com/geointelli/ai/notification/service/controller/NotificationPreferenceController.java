package com.geointelli.ai.notification.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesDTO;
import com.geointelli.ai.notification.service.dto.NotificationPreferencesRequest;
import com.geointelli.ai.notification.service.service.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Current-user delivery choices. Does not change saved-search filters or
 * evaluation cadence.
 */
@RestController
@RequestMapping("/api/notifications/preferences")
@Tag(name = "Notification preferences")
@SecurityRequirement(name = "bearerAuth")
public class NotificationPreferenceController {
    private final NotificationPreferenceService preferences;

    public NotificationPreferenceController(NotificationPreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    @Operation(summary = "Get my notification preferences", description = "Defaults: in-app=true, email=false, enabledEventTypes=[NEW_MATCH, CONTACT_REQUEST, VALUATION_CHANGED], emailCadence=DAILY. "
            + "EmailDeliveryEnabled reports whether the server's SES worker is enabled; it is not a delivery receipt.")
    public NotificationPreferencesDTO get() {
        return preferences.get();
    }

    @PutMapping
    @Operation(summary = "Replace my notification preferences", description = "All four request fields are required. Supported events: NEW_MATCH, CONTACT_REQUEST and VALUATION_CHANGED; [] opts out of all.  "
            + "Email opt-in requires email_verified=true and an email claim in the signed JWT; no client-supplied recipient is accepted. "
            + "IMMEDIATE sends one notification per email; DAILY and WEEKLY produce bounded digests at UTC midnight or Monday UTC midnight. "
            + "No historical notifications are backfilled. Previously visible inbox items remain visible. Opt-outs suppress unsent queued emails; "
            + "routing changes cancel retry batches under the old settings. Already dispatching/accepted messages cannot be recalled. "
            + "Repeated identical PUTs preserve the next digest time. Missing/invalid input returns 400; missing bearer identity returns 401.")
    public NotificationPreferencesDTO update(@Valid @RequestBody NotificationPreferencesRequest request) {
        return preferences.update(request);
    }
}
