package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.LenderApplicationImportRequest;
import com.geointelli.ai.property.service.dto.LenderApplicationImportResult;
import com.geointelli.ai.property.service.service.LenderApplicationImportService;
import com.geointelli.ai.property.service.service.LenderApplicationService;
import com.geointelli.ai.property.service.service.LenderApplicationValidationService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import lombok.RequiredArgsConstructor;

/** Nontransactional batch orchestrator: rejected rows roll back without undoing successful rows. */
@Service
@RequiredArgsConstructor
public class LenderApplicationImportServiceImpl implements LenderApplicationImportService {
    private final LenderApplicationService applications;
    private final LenderApplicationValidationService validation;
    private final LenderOrganizationService organizations;

    @Override
    public LenderApplicationImportResult importApplications(Long actor, Long organizationId, LenderApplicationImportRequest request) {
        organizations.requireAccess(actor, organizationId, false);
        if (request == null || request.rows() == null || request.rows().isEmpty() || request.rows().size() > 100)
            throw new IllegalArgumentException("rows must contain between 1 and 100 complete snapshots");
        String source = validation.source(request.source());
        List<LenderApplicationImportResult.RowResult> results = new ArrayList<>();
        int created = 0, updated = 0, unchanged = 0, rejected = 0;
        for (int index = 0; index < request.rows().size(); index++) {
            LenderApplicationImportResult.RowResult result;
            try {
                result = applications.importRow(actor, organizationId, source, index + 1, request.rows().get(index));
            } catch (IllegalArgumentException error) {
                result = rejected(index, "INVALID_ROW");
            } catch (ResponseStatusException error) {
                // Stop if membership was revoked between rows; do not reveal or continue tenant processing.
                if (error.getStatusCode().value() == 404 || error.getStatusCode().value() == 401
                        || error.getStatusCode().value() == 403) throw error;
                result = rejected(index, "INVALID_TRANSITION");
            } catch (DataIntegrityViolationException | PessimisticLockingFailureException error) {
                result = rejected(index, "CONFLICT_RETRY_ROW");
            }
            results.add(result);
            switch (result.status()) {
                case "CREATED" -> created++;
                case "UPDATED" -> updated++;
                case "UNCHANGED" -> unchanged++;
                default -> rejected++;
            }
        }
        return new LenderApplicationImportResult(created, updated, unchanged, rejected, List.copyOf(results));
    }

    private LenderApplicationImportResult.RowResult rejected(int index, String code) {
        return new LenderApplicationImportResult.RowResult(index + 1, "REJECTED", null, code);
    }
}

