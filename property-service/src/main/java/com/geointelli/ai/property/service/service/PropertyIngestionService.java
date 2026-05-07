package com.geointelli.ai.property.service.service;

public interface PropertyIngestionService {
    void ingest(String folio);
    void ingestBuildings(String folio);
}