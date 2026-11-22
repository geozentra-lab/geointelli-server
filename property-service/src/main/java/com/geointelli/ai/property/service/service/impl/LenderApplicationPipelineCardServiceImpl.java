package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.LenderApplicationPipelineCardDTO;
import com.geointelli.ai.property.service.entity.LenderApplicationStage;
import com.geointelli.ai.property.service.repository.LenderApplicationRepository;
import com.geointelli.ai.property.service.repository.LenderPipelineCount;
import com.geointelli.ai.property.service.service.LenderApplicationPipelineCardService;
import com.geointelli.ai.property.service.service.LenderOrganizationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Counts the organization's applications by current stage with the SCRUM-82 pipeline aggregation (one GROUP BY
 * query), so the card never loads the applications themselves and stays independent of list pagination.
 */
@Service
public class LenderApplicationPipelineCardServiceImpl implements LenderApplicationPipelineCardService {


    static final String NOTE = "Recorded applications of the organization counted by their current stage, as of "
            + "generatedAt. Each application is counted once, in the stage it is in now; the counts are not "
            + "movements over a period. Every supported stage is listed, 0 included.";

    private final LenderOrganizationService organizations;
    private final LenderApplicationRepository applications;
    private final Clock clock;

    public LenderApplicationPipelineCardServiceImpl(LenderOrganizationService organizations,
                                                    LenderApplicationRepository applications,
                                                    @Qualifier("lenderDashboardClock") Clock clock) {
        this.organizations = organizations;
        this.applications = applications;
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public LenderApplicationPipelineCardDTO getCard(Long actor, Long organizationId) {
        // Membership first: a foreign organization is the same 404 as a missing one
        organizations.requireAccess(actor, organizationId, false);
        Instant generatedAt = clock.instant();

        // Counts per stage from the database
        Map<LenderApplicationStage, Long> counts = new EnumMap<>(LenderApplicationStage.class);
        for (LenderPipelineCount row : applications.pipeline(organizationId)) {
            counts.put(row.getStage(), row.getCount());
        }

        // Every supported stage in pipeline order, 0 when no application is in it
        List<LenderApplicationPipelineCardDTO.StageCount> stages = new ArrayList<>();
        long total = 0;
        for (LenderApplicationStage stage : LenderApplicationStage.values()) {
            long count = counts.getOrDefault(stage, 0L);
            stages.add(new LenderApplicationPipelineCardDTO.StageCount(stage, count));
            total += count;
        }

        String status = total == 0
                ? LenderApplicationPipelineCardDTO.STATUS_EMPTY : LenderApplicationPipelineCardDTO.STATUS_OK;
        return new LenderApplicationPipelineCardDTO(organizationId, status, null, generatedAt,
                new LenderApplicationPipelineCardDTO.ReportingPeriod(LenderApplicationPipelineCardDTO.PERIOD_TYPE, generatedAt,
                        LenderApplicationPipelineCardDTO.PERIOD_DESCRIPTION),
                total, List.copyOf(stages), NOTE);
    }
}
