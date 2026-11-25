package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.entity.IngestionJob;
import com.geointelli.ai.property.service.enums.ImportJobType;
import com.geointelli.ai.property.service.enums.IngestionDataset;
import com.geointelli.ai.property.service.service.IngestionJobContext;
import com.geointelli.ai.property.service.service.IngestionJobService.Counters;
import com.geointelli.ai.property.service.service.IngestionJobWorker;
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;
import com.geointelli.ai.property.service.service.SchoolAssignmentService;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.AssignmentResult;
import com.geointelli.ai.property.service.service.SchoolAssignmentService.SchoolCounty;
import com.geointelli.ai.property.service.service.SchoolScoreV2Service;
import com.geointelli.ai.property.service.service.SchoolScoreV2Service.ScoreResult;

import lombok.extern.slf4j.Slf4j;

/**
 * Runs the school scoring jobs (SCRUM-203), county by county. An assignment job assigns the county's schools in one
 * transaction, then computes its school score v2 in a second one; a score job only computes the score. Each
 * transaction also records the job's counters, so a failure leaves the county's previous rows untouched and a retry
 * simply runs the county again (both steps write only what changed).
 * <p>
 * Counters: total is the properties to handle (with a parcel for an assignment, assigned for a score); completed the
 * properties that got a school (assignment) or a score; skipped the others.
 */
@Service
@Slf4j
public class SchoolScoringJobWorker implements IngestionJobWorker {
    static final String DATABASE_ERROR = CsvIngestionJobWorker.DATABASE_ERROR;
    static final String SCOPE_UNAVAILABLE = ReferenceDataIngestionJobWorker.SCOPE_UNAVAILABLE;
    static final String NOTHING_TO_SCORE = "NOTHING_TO_SCORE";
    static final int STEP_LENGTH = 120;

    private final SchoolAssignmentService assignments;
    private final SchoolScoreV2Service scores;
    private final ReferenceDataScopeService scopes;

    public SchoolScoringJobWorker(SchoolAssignmentService assignments, SchoolScoreV2Service scores,
            ReferenceDataScopeService scopes) {
        this.assignments = assignments;
        this.scores = scores;
        this.scopes = scopes;
    }

    @Override
    public boolean supports(IngestionJob job) {
        return job.getJobType() == ImportJobType.SCHOOL_SCORING;
    }

    @Override
    public Result run(IngestionJobContext context) throws Exception {
        IngestionJob job = context.job();
        boolean assign = job.getDataset() == IngestionDataset.SCHOOL_ASSIGNMENT;
        List<SchoolCounty> counties;
        try {
            String fips5 = null;
            if (job.getCounty() != null) {
                fips5 = scopes.countyFips(job.getCounty().getId()).orElse(null);
                if (fips5 == null) {
                    return Result.failed(SCOPE_UNAVAILABLE, "The county " + job.getSourceCounty() + " has no FIPS code");
                }
            }
            counties = assign ? assignments.countiesToAssign(fips5) : scores.countiesToScore(fips5);
        } catch (IllegalStateException unavailable) {
            return Result.failed(SCOPE_UNAVAILABLE, unavailable.getMessage());
        }
        if (counties.isEmpty()) {
            return Result.failed(NOTHING_TO_SCORE, assign ? "No county has attendance zones and properties"
                : "No county has school assignments");
        }

        int total = 0;
        for (SchoolCounty county : counties) {
            total += assign ? assignments.countPropertiesToAssign(county) : scores.countPropertiesToScore(county);
        }
        Counters counters = new Counters(total, 0, 0, 0);
        context.progress(counters, (assign ? "Assigning " : "Scoring ") + fips(counties), checkpoint("STARTED", null));

        List<String> done = new ArrayList<>();
        for (SchoolCounty county : counties) {
            context.checkWorker();
            String fips5 = county.fips5();
            try {
                if (assign) {
                    AssignmentResult[] assigned = new AssignmentResult[1];
                    Counters before = counters;
                    counters = context.commit(() -> {
                        assigned[0] = assignments.assignCounty(fips5);
                        int properties = assigned[0].properties();
                        return before.plus(assigned[0].assignedProperties(),
                            properties - assigned[0].assignedProperties(), 0);
                    }, fips5 + ": assigned", checkpoint("ASSIGNED", fips5));
                    String summary = fips5 + " " + assigned[0].summary();
                    Counters after = counters;
                    ScoreResult[] scored = new ScoreResult[1];
                    context.commit(() -> {
                        scored[0] = scores.scoreCounty(fips5);
                        return after;
                    }, step(summary + ", scored"), checkpoint("SCORED", fips5));
                    context.progress(counters, step(summary + ", scored " + scored[0].scored()),
                        checkpoint("SCORED", fips5));
                } else {
                    ScoreResult[] scored = new ScoreResult[1];
                    Counters before = counters;
                    counters = context.commit(() -> {
                        scored[0] = scores.scoreCounty(fips5);
                        return before.plus(scored[0].scored(), scored[0].properties() - scored[0].scored(), 0);
                    }, step(fips5 + ": scored"), checkpoint("SCORED", fips5));
                    context.progress(counters, step(fips5 + ": scored " + scored[0].scored() + " of "
                        + scored[0].properties() + " (FLDOE " + scored[0].gradeYear() + ")"), checkpoint("SCORED", fips5));
                }
            } catch (DataAccessException databaseError) {
                return fail(context, counters, fips5, DATABASE_ERROR, databaseError.getMostSpecificCause().getMessage());
            } catch (IllegalArgumentException | IllegalStateException unavailable) {
                return fail(context, counters, fips5, SCOPE_UNAVAILABLE, unavailable.getMessage());
            }
            done.add(fips5);
        }
        log.info("School scoring job {} ({}) finished for {}", job.getId(), job.getDataset(), done);
        return Result.completed();
    }

    /** Records the failure as an item; the county's transaction was rolled back. */
    private Result fail(IngestionJobContext context, Counters counters, String fips5, String reason, String message)
            throws InterruptedException {
        String text = CsvIngestionJobWorker.sanitize(message);
        context.item(fips5, "FAILED", reason, text);
        context.progress(counters, step(fips5 + ": failed"), checkpoint("FAILED", fips5));
        return Result.failed(reason, fips5 + ": " + text);
    }

    private static String fips(List<SchoolCounty> counties) {
        return counties.size() == 1 ? counties.get(0).fips5() : counties.size() + " counties";
    }

    private static String step(String text) {
        return text.length() <= STEP_LENGTH ? text : text.substring(0, STEP_LENGTH);
    }

    private static String checkpoint(String phase, String fips5) {
        return fips5 == null ? "{\"phase\":\"" + phase + "\"}"
            : "{\"phase\":\"" + phase + "\",\"fips5\":\"" + fips5 + "\"}";
    }
}
