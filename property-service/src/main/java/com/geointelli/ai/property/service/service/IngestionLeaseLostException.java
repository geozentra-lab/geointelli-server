package com.geointelli.ai.property.service.service;

/** Thrown when a job's lease expired or another worker took the job; the current worker must stop. */
public class IngestionLeaseLostException extends RuntimeException {
    public IngestionLeaseLostException() {
        super("The job lease was lost");
    }
}
