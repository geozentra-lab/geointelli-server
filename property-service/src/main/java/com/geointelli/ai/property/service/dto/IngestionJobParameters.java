package com.geointelli.ai.property.service.dto;

import java.util.List;

/** Start parameters stored with an ingestion job: the input files it owns, with their size and change marker. */
public record IngestionJobParameters(List<StoredInputRef> inputs) {

    /** One stored input file. */
    public record StoredInputRef(String name, long size, String etag) { }

    public boolean has(String name) {
        return inputs != null && inputs.stream().anyMatch(input -> input.name().equals(name));
    }
}
