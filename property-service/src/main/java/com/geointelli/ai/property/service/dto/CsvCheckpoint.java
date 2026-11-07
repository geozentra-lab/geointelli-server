package com.geointelli.ai.property.service.dto;

import java.util.List;

/**
 * Where a CSV job continues: the step (file) index, the phase within the file, the number of rows already
 * committed in that phase, and which steps already added their row count to the job total.
 */
public record CsvCheckpoint(int stepIndex, String phase, long committedRows, List<Integer> countedSteps) {
    public static final String ROWS = "ROWS";
    /** Second pass over properties.csv that links children to parents. */
    public static final String PARENTS = "PARENTS";

    public static CsvCheckpoint start() { return new CsvCheckpoint(0, ROWS, 0, List.of()); }

    public CsvCheckpoint withCommitted(long rows) { return new CsvCheckpoint(stepIndex, phase, rows, countedSteps); }

    public CsvCheckpoint atPhase(int step, String newPhase) { return new CsvCheckpoint(step, newPhase, 0, countedSteps); }

    public CsvCheckpoint counted(int step) {
        if (isCounted(step)) return this;
        List<Integer> steps = new java.util.ArrayList<>(countedSteps == null ? List.of() : countedSteps);
        steps.add(step);
        return new CsvCheckpoint(stepIndex, phase, committedRows, List.copyOf(steps));
    }

    public boolean isCounted(int step) { return countedSteps != null && countedSteps.contains(step); }

    /** Rows to skip when resuming the given step and phase. */
    public long committedRowsFor(int step, String atPhase) {
        return stepIndex == step && phase.equals(atPhase) ? committedRows : 0;
    }
}
