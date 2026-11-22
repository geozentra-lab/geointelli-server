package com.geointelli.ai.property.service.dto;

import java.util.List;

/**
 * Start parameters stored with an ingestion job: the input files it owns, with their size and change marker,
 * and for reference data (SCRUM-201) the date the source was downloaded (ISO yyyy-MM-dd), else null.
 */
public record IngestionJobParameters(List<StoredInputRef> inputs, String downloadedOn) {

    /** One stored input file. */
    public record StoredInputRef(String name, long size, String etag) { }

    public IngestionJobParameters(List<StoredInputRef> inputs) {
        this(inputs, null);
    }

    public boolean has(String name) {
        return inputs != null && inputs.stream().anyMatch(input -> input.name().equals(name));
    }
}
