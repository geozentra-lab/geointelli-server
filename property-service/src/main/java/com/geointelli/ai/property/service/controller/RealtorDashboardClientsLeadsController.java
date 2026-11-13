package com.geointelli.ai.property.service.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO;
import com.geointelli.ai.property.service.service.RealtorDashboardClientsLeadsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Realtor dashboard, clients-and-leads card (SCRUM-108). One class per card so that the sibling
 * cards (SCRUM-109 to 111) and the composed GET /api/dashboards/realtor (SCRUM-112) can be added
 * without touching this file. Scope is the bearer-token user; persona is not checked.
 */
@RestController
@RequestMapping("/api/dashboards/realtor")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Realtor dashboard", description = "Cards of the realtor dashboard, read as the current user. "
    + "Missing identity 401; invalid input 400.")
public class RealtorDashboardClientsLeadsController {
    private final RealtorDashboardClientsLeadsService service;

    @GetMapping("/clients-leads")
    @Operation(summary = "Clients and leads card",
        description = "Counts the current user's CRM contacts and leads (leads per stored stage: NEW, CONTACTED, "
            + "QUALIFIED, CONVERTED, LOST; open = the first three) and returns the newest of each, at most limit "
            + "items per list (1-20, default 5). Users without CRM data get zeros, a stage map of zeros and empty "
            + "lists with status OK. 400 when limit is out of range or not a number; 401 without a bearer token.")
    public RealtorClientsLeadsCardDTO clientsLeads(
            @RequestParam(defaultValue = "" + RealtorDashboardClientsLeadsService.DEFAULT_LIMIT) int limit) {
        return service.getClientsLeadsCard(limit);
    }
}
