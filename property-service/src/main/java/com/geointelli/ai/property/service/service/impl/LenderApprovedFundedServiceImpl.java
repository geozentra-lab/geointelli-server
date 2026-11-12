package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.LenderApprovedFundedCardDTO;
import com.geointelli.ai.property.service.dto.LenderApprovedFundedCardDTO.Period;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.entity.LenderLoanType;
import com.geointelli.ai.property.service.repository.LenderApplicationRepository;
import com.geointelli.ai.property.service.repository.LenderLoanTypeTotal;
import com.geointelli.ai.property.service.service.LenderApprovedFundedService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;

/**
 * Reads the approved and funded totals per loan type with two database aggregations, so each application is
 * counted once per metric whatever the number of applications. The funded total is the sum of the loan mix,
 * so both always agree.
 */
@Service
public class LenderApprovedFundedServiceImpl implements LenderApprovedFundedService {

    static final List<LenderApplicationStage> APPROVED_STAGES =
            List.of(LenderApplicationStage.APPROVED, LenderApplicationStage.FUNDED);
    static final String NOTE = "Recorded USD applications of the organization. approved = approvedAmount of "
            + "applications approved in the period (approvedAt) and still APPROVED or FUNDED; withdrawn approvals are "
            + "excluded. funded = fundedAmount of FUNDED applications funded in the period (fundedAt); funded "
            + "applications are also counted in approved when approved in the same period, so do not add the two. "
            + "fundedLoanMix splits exactly the funded applications by loan type; UNKNOWN means no type was recorded.";

    private final LenderOrganizationService organizations;
    private final LenderApplicationRepository applications;
    private final Clock clock;

    public LenderApprovedFundedServiceImpl(LenderOrganizationService organizations,
                                           LenderApplicationRepository applications,
                                           @Qualifier("lenderDashboardClock") Clock clock) {
        this.organizations = organizations;
        this.applications = applications;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public LenderApprovedFundedCardDTO getCard(Long actor, Long organizationId, LocalDate fromDate, LocalDate toDate) {
        // Membership first: a foreign organization is the same 404 as a missing one
        organizations.requireAccess(actor, organizationId, false);
        Instant generatedAt = clock.instant();

        // Period: both dates or none (current UTC month)
        if ((fromDate == null) != (toDate == null)) {
            throw new IllegalArgumentException("fromDate and toDate must be given together");
        }
        if (fromDate == null) {
            LocalDate today = generatedAt.atZone(ZoneOffset.UTC).toLocalDate();
            fromDate = today.withDayOfMonth(1);
            toDate = today.withDayOfMonth(today.lengthOfMonth());
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        Instant from = fromDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();


        // Approved: one count and amount over all loan types
        long approvedCount = 0;
        BigDecimal approvedAmount = BigDecimal.ZERO;
        for (LenderLoanTypeTotal row : applications.approvedByLoanType(organizationId, APPROVED_STAGES, from, to)) {
            approvedCount += row.getCount();
            approvedAmount = approvedAmount.add(zeroIfNull(row.getTotal()));
        }

        // Funded: every loan type is listed, even with 0, and the mix adds up to the funded total
        Map<LenderLoanType, LenderLoanTypeTotal> fundedRows = new EnumMap<>(LenderLoanType.class);
        for (LenderLoanTypeTotal row : applications.fundedByLoanType(organizationId,
                LenderApplicationStage.FUNDED, from, to)) {
            fundedRows.put(row.getLoanType(), row);
        }
        List<LenderApprovedFundedCardDTO.LoanMixEntry> mix = new ArrayList<>();
        long fundedCount = 0;
        BigDecimal fundedAmount = BigDecimal.ZERO;
        for (LenderLoanType loanType : LenderLoanType.values()) {
            LenderLoanTypeTotal row = fundedRows.get(loanType);
            long count = row == null ? 0 : row.getCount();
            BigDecimal amount = row == null ? BigDecimal.ZERO : zeroIfNull(row.getTotal());
            mix.add(new LenderApprovedFundedCardDTO.LoanMixEntry(loanType, count, money(amount)));
            fundedCount += count;
            fundedAmount = fundedAmount.add(amount);
        }
        String status = approvedCount == 0 && fundedCount == 0
                ? LenderApprovedFundedCardDTO.STATUS_EMPTY : LenderApprovedFundedCardDTO.STATUS_OK;
        return new LenderApprovedFundedCardDTO(organizationId, "USD", status, null, generatedAt,
                new Period(fromDate, toDate, LenderApprovedFundedCardDTO.TIME_ZONE),
                new LenderApprovedFundedCardDTO.AmountTotal(approvedCount, money(approvedAmount)),
                new LenderApprovedFundedCardDTO.AmountTotal(fundedCount, money(fundedAmount)),
                List.copyOf(mix), NOTE);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
