package com.geointelli.ai.property.service.service;

import com.geointelli.ai.property.service.dto.LenderApprovedFundedCardDTO;

import java.time.LocalDate;

/**
 * Builds the approved, funded and loan-mix card of the lender dashboard (SCRUM-114) from the organization's
 * recorded applications (SCRUM-82). Only applications of an organization the user belongs to are read; nothing
 * is changed.
 */
public interface LenderApprovedFundedService {
    /**
     * Adds up, for one organization and period, the applications approved (approvedAmount, dated by approvedAt,
     * still APPROVED or FUNDED) and funded (fundedAmount, dated by fundedAt), and splits the funded ones by loan
     * type. Each application is counted once per metric; a missing loan type is shown as UNKNOWN.
     *
     * @param actor          the authenticated user
     * @param organizationId the organization whose applications are read
     * @param fromDate       first day of the period (UTC, included); with toDate, or both null for the current month
     * @param toDate         last day of the period (UTC, included); with fromDate, or both null for the current month
     * @return the card, with status OK or EMPTY
     * @throws IllegalArgumentException if organizationId is not positive, only one date is given, or fromDate is
     *         after toDate
     * @throws org.springframework.web.server.ResponseStatusException 404 if the organization is missing or the user
     *         is not a member
     */
    LenderApprovedFundedCardDTO getCard(Long actor, Long organizationId, LocalDate fromDate, LocalDate toDate);
}
