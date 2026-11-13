package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.LenderApprovedFundedCardDTO;
import com.geointelli.ai.property.service.dto.MortgageContextCardDTO;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.LenderApprovedFundedService;
import com.geointelli.ai.property.service.service.LenderDashboardMortgageContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.MortgageCalculationRequest;

import java.time.LocalDate;

/**
 * Bank or lender dashboard (SCRUM-87). Each card has its own endpoint so it can be verified alone;
 * the composed lender dashboard is added by SCRUM-117.
 */
@RestController
@RequestMapping("/api/dashboards/lender")
@RequiredArgsConstructor
@Tag(name = "Lender dashboard", description = "Cards of the bank or lender dashboard.")
public class LenderDashboardController {

    private final LenderDashboardMortgageContextService mortgageContextService;
    private final LenderApprovedFundedService approvedFundedService;
    private final CurrentUserService currentUser;

    /**
     * Mortgage context card: the mortgage calculation for the supplied inputs.
     *
     * @param inputs price, downPayment, annualInterestRate (%), loanTermYears and optional
     *               annualPropertyTax, annualHomeInsurance, monthlyHoaFees as query parameters; all may be omitted
     * @return 200 with status OK, or UNAVAILABLE when no input is supplied; 400 if inputs are
     *         partial, out of range, not a number, or the down payment exceeds the price
     */
    @GetMapping("/mortgage-context")
    @Operation(summary = "Mortgage context card of the lender dashboard", description = "Reuses the mortgage "
            + "calculator on the supplied inputs and discloses the rate source and calculation date. "
            + "No live mortgage-rate feed is used. Without any input the card is UNAVAILABLE.")
    public ResponseEntity<MortgageContextCardDTO> mortgageContext(@ParameterObject MortgageCalculationRequest inputs) {
        return ResponseEntity.ok(mortgageContextService.getMortgageContextCard(inputs));
    }


    /**
     * Approved, funded and loan-mix card: recorded application amounts of one organization over a period.
     *
     * @param organizationId organization the current user belongs to
     * @param fromDate       first day (UTC, included); give both dates or none for the current month
     * @param toDate         last day (UTC, included)
     * @return 200 with the card (EMPTY without approved or funded applications); 400 if organizationId is missing
     *         or not positive, a date is not yyyy-MM-dd, only one date is given or fromDate is after toDate; 401
     *         without a bearer token; 404 if the organization is missing or the user is not a member
     */
    @GetMapping("/approved-funded")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Approved, funded and loan-mix card of the lender dashboard", description = "Recorded USD "
            + "applications of organizationId (membership required). approved = approvedAmount of applications approved "
            + "in the period (approvedAt) and still APPROVED or FUNDED; funded = fundedAmount of FUNDED applications "
            + "funded in the period (fundedAt). fundedLoanMix splits the funded applications by loan type, every type "
            + "listed and UNKNOWN when none was recorded. Dates are UTC days, both included; without dates the current "
            + "month is used.")
    public ResponseEntity<LenderApprovedFundedCardDTO> approvedFunded(
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        Long actor = currentUser.getCurrentUserEntity().getId();
        return ResponseEntity.ok(approvedFundedService.getCard(actor, organizationId, fromDate, toDate));
    }
}
