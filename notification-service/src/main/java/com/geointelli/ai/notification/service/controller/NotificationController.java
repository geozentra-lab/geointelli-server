package com.geointelli.ai.notification.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.notification.service.dto.NotificationDTO;
import com.geointelli.ai.notification.service.dto.NotificationPageDTO;
import com.geointelli.ai.notification.service.dto.NotificationReadRequest;
import com.geointelli.ai.notification.service.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Current-user notification inbox; ownership comes from the verified bearer
 * token, never request data.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Read your inbox and mark items read. Requires a Keycloak bearer token.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    @Operation(summary = "List my notifications", description = "Zero-based page, size 1 to 100 (default 20). "
            + "Newest createdAt first, then ID descending for ties. Includes read and readAt. "
            + "An empty inbox returns content=[], totalElements=0 and totalPages=0. No user ID parameter is accepted for ownership.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Owned notification page"),
            @ApiResponse(responseCode = "400", description = "Invalid pagination"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token") })
    public NotificationPageDTO list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notifications.list(page, size);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Mark my notification read", description = "Send {\"read\":true}. Returns the updated item. "
            + "Repeated and concurrent requests preserve the first readAt. false, null, missing read and invalid IDs return 400. "
            + "Missing and other-user IDs both return 404. No notification content or ownership can be changed.")
    @ApiResponses({ @ApiResponse(responseCode = "200", description = "Owned notification with read=true"),
            @ApiResponse(responseCode = "400", description = "Invalid ID or request body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
            @ApiResponse(responseCode = "404", description = "Owned notification not found") })
    public NotificationDTO markRead(@PathVariable Long id, @Valid @RequestBody NotificationReadRequest request) {
        return notifications.markRead(id, request);
    }
}
