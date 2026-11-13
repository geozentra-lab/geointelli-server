package com.geointelli.ai.property.service.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.geointelli.ai.property.service.entity.LeadStage;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Clients-and-leads card of the realtor dashboard (SCRUM-108): the current user's CRM contacts and
 * enquiries (SCRUM-78), counted from their persisted rows, plus the newest of each. Scope is the
 * bearer-token user who owns the records; persona is not checked, so a non-realtor simply sees zeros.
 */
@Data
public class RealtorClientsLeadsCardDTO {
    /** Value of {@link #scope} for every dashboard card that is read as the current user. */
    public static final String SCOPE_CURRENT_USER = "CURRENT_USER";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    @Schema(description = "Whose data the card shows; always CURRENT_USER", example = SCOPE_CURRENT_USER)
    private String scope;

    @Schema(description = "Freshness: when the card was built (server time)")
    private LocalDateTime generatedAt;

    @Schema(description = "OK when the data loaded; ERROR when the dashboard could not load this card", example = STATUS_OK)
    private String status;

    @Schema(description = "Why the card failed; null when status is OK")
    private String error;

    @Schema(description = "Maximum number of items in each recent list", example = "5")
    private int limit;

    @Schema(description = "The user's CRM contacts")
    private ClientsSection clients;

    @Schema(description = "The user's CRM enquiries, counted by their stored stage")
    private LeadsSection leads;

    /** Contact counts and the newest contacts. */
    public record ClientsSection(
        @Schema(description = "Total number of contacts owned by the user", example = "12") long total,
        @Schema(description = "Newest contacts first, at most limit items; empty when the user has none")
        List<ClientItem> recent) {
    }

    /** One contact, summarized for display; notes are omitted. Links to GET /api/clients/{id}. */
    public record ClientItem(
        Long id,
        String name,
        @Schema(description = "Normalized email, or null") String email,
        @Schema(description = "Phone, or null") String phone,
        @Schema(description = "When the contact was created") LocalDateTime createdAt) {
    }

    /** Enquiry counts by stage and the newest enquiries. Each lead is in exactly one stage. */
    public record LeadsSection(
        @Schema(description = "Total number of leads owned by the user (sum of byStage)", example = "7") long total,
        @Schema(description = "Leads still in progress: NEW + CONTACTED + QUALIFIED", example = "4") long open,
        @Schema(description = "Count per stage; every stage is present, 0 when empty",
            example = "{\"NEW\": 2, \"CONTACTED\": 1, \"QUALIFIED\": 1, \"CONVERTED\": 2, \"LOST\": 1}")
        Map<LeadStage, Long> byStage,
        @Schema(description = "Newest leads first, at most limit items; empty when the user has none")
        List<LeadItem> recent) {
    }

    /** One enquiry, summarized for display. Links to GET /api/leads/{id} and GET /api/clients/{clientId}. */
    public record LeadItem(
        Long id,
        @Schema(description = "The contact who made the enquiry") Long clientId,
        @Schema(description = "That contact's name, for display") String clientName,
        @Schema(description = "Property the enquiry is about, or null") Long propertyId,
        String title,
        @Schema(description = "Where the enquiry came from, e.g. WEBSITE") String source,
        @Schema(description = "Stored stage: NEW, CONTACTED, QUALIFIED, CONVERTED or LOST") LeadStage stage,
        @Schema(description = "When the lead was captured") LocalDateTime createdAt) {
    }
}
