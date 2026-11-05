package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.entity.LenderApplication;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.LenderApplicationValidationService;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;

/** Validates USD snapshots without assuming missing approval, funding or property facts. */
@Service
@RequiredArgsConstructor
public class LenderApplicationValidationServiceImpl implements LenderApplicationValidationService {
    private final PropertyRepository properties;
    private final Validator validator;

    private record EmailValue(@Email String email) {}

    @Override
    public void validate(LenderApplication value) {
        value.setApplicantName(text(value.getApplicantName(), "applicantName", 160, true));
        value.setApplicantEmail(text(value.getApplicantEmail(), "applicantEmail", 254, false));
        if (value.getApplicantEmail() != null) {
            value.setApplicantEmail(value.getApplicantEmail().toLowerCase(Locale.ROOT));
            if (!validator.validate(new EmailValue(value.getApplicantEmail())).isEmpty())
                throw new IllegalArgumentException("applicantEmail must be a valid email address");
        }
        value.setNotes(text(value.getNotes(), "notes", 2000, false));
        String currency = text(value.getCurrency(), "currency", 3, true).toUpperCase(Locale.ROOT);
        if (!"USD".equals(currency)) throw new IllegalArgumentException("Only USD is supported");
        value.setCurrency(currency);
        value.setRequestedAmount(money(value.getRequestedAmount(), "requestedAmount", true));
        value.setApprovedAmount(money(value.getApprovedAmount(), "approvedAmount", false));
        value.setFundedAmount(money(value.getFundedAmount(), "fundedAmount", false));
        if (value.getPropertyId() != null && (value.getPropertyId() <= 0 || !properties.existsById(value.getPropertyId())))
            throw new IllegalArgumentException("propertyId must identify an existing property");
        LenderApplicationStage stage = value.getStage();
        if (stage == null) throw new IllegalArgumentException("stage is required");
        value.setApprovedAt(time(value.getApprovedAt(), "approvedAt"));
        value.setFundedAt(time(value.getFundedAt(), "fundedAt"));
        boolean approvalRequired = stage == LenderApplicationStage.APPROVED || stage == LenderApplicationStage.FUNDED;
        boolean hasApproval = value.getApprovedAmount() != null || value.getApprovedAt() != null;
        if ((approvalRequired || hasApproval) && (value.getApprovedAmount() == null || value.getApprovedAt() == null))
            throw new IllegalArgumentException("Approval requires approvedAmount and approvedAt");
        if (hasApproval && !approvalRequired && stage != LenderApplicationStage.WITHDRAWN)
            throw new IllegalArgumentException("Approval is not allowed in this stage");
        if (value.getApprovedAmount() != null && value.getApprovedAmount().compareTo(value.getRequestedAmount()) > 0)
            throw new IllegalArgumentException("approvedAmount must not exceed requestedAmount");
        if (stage == LenderApplicationStage.FUNDED) {
            if (value.getFundedAmount() == null || value.getFundedAt() == null)
                throw new IllegalArgumentException("FUNDED requires fundedAmount and fundedAt");
            if (value.getFundedAmount().compareTo(value.getApprovedAmount()) > 0)
                throw new IllegalArgumentException("fundedAmount must not exceed approvedAmount");
            if (value.getFundedAt().isBefore(value.getApprovedAt()))
                throw new IllegalArgumentException("fundedAt must not precede approvedAt");
        } else if (value.getFundedAmount() != null || value.getFundedAt() != null) {
            throw new IllegalArgumentException("Funding is only allowed in FUNDED stage");
        }
    }

    @Override
    public String source(String value) {
        return text(value, "source", 80, true).toUpperCase(Locale.ROOT);
    }

    @Override
    public String externalId(String value) { return text(value, "externalId", 160, true); }

    private String text(String value, String field, int max, boolean required) {
        String result = value == null ? null : value.trim();
        if (result != null && result.isEmpty()) result = null;
        if (required && result == null) throw new IllegalArgumentException(field + " is required");
        if (result != null && (result.length() > max || result.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')))
            throw new IllegalArgumentException(field + " is invalid or exceeds " + max + " characters");
        return result;
    }

    private BigDecimal money(BigDecimal value, String field, boolean required) {
        if (value == null) {
            if (required) throw new IllegalArgumentException(field + " is required");
            return null;
        }
        if (value.signum() <= 0 || value.compareTo(new BigDecimal("9999999999999999.99")) > 0
                || value.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException(field + " must be positive, at most 9999999999999999.99, with at most two decimals");
        return value.setScale(2);
    }

    private Instant time(Instant value, String field) {
        if (value == null) return null;
        Instant result = value.truncatedTo(ChronoUnit.MICROS);
        if (result.isBefore(Instant.parse("1900-01-01T00:00:00Z")) || result.isAfter(Instant.now()))
            throw new IllegalArgumentException(field + " must be between 1900 and now");
        return result;
    }
}

