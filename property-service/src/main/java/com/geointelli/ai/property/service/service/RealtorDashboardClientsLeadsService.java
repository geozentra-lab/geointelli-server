package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.RealtorClientsLeadsCardDTO;

/**
 * Builds the clients-and-leads card of the realtor dashboard (SCRUM-108) for the current user.
 * Reads the CRM records of SCRUM-78 (contacts and leads) through their repositories; it never
 * modifies them. Access is by record ownership: only rows whose owner is the bearer-token user are
 * read. Persona is not checked, because every persona shares normal CRM capabilities.
 */
public interface RealtorDashboardClientsLeadsService {
    /** Size of each recent list when the caller does not pass a limit. */
    int DEFAULT_LIMIT = 5;
    /** Largest accepted limit; the card is a summary, not a listing. */
    int MAX_LIMIT = 20;

    /**
     * Builds the card: total contacts, total and per-stage leads, and the newest contacts and leads.
     * Users without CRM data get zeros, a stage map of zeros and empty lists.
     *
     * @param limit maximum items in each recent list; must be between 1 and {@link #MAX_LIMIT}
     * @return the populated card with status OK
     * @throws IllegalArgumentException when limit is out of range (mapped to 400)
     */
    RealtorClientsLeadsCardDTO getClientsLeadsCard(int limit);
}
