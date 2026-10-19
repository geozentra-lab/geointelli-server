package com.geointelli.ai.property.service.service;

public interface PropertyIngestionService {
    void ingest(String folio);
    void ingestBuildings(String folio);
    void ingestAddresses(String folio);
    void ingestExtraFeatures(String folio);
    void ingestSales(String folio);
}