package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.nio.file.Path;

/**
 * CSV headers use snake_case entity attributes and property_id for the source property.
 * Import properties first. Generated IDs and audit timestamps are managed by persistence.
 * Dates use yyyy-MM-dd; parcel geom uses WKT MULTIPOLYGON in EPSG:4326 and id is required.
 * Owner CSVs use id as the source owner ID and do not require property_id.
 * importOwners accepts both the owner CSV and the property_owner CSV (property_id, owner_id).
 * Source owner IDs are mapped in memory for this import; properties must already be imported.
 * Link CSV timestamp columns are ignored; the many-to-many association has no audit fields.
 */
public interface CsvImportService {

    void importProperties(Path csvPath, String sourceCounty, String sourceState) throws IOException;
    
    void importAddresses(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importAssessments(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importBuildings(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importOwners(Path ownersCsvPath, Path propertyOwnersCsvPath, String sourceCounty, String sourceState) throws IOException;

    void importLands(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importSales(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importTaxes(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importExtraFeatures(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importParcels(Path csvPath, String sourceCounty, String sourceState) throws IOException;

    void importPropertyValuePredictions(Path csvPath, String sourceCounty, String sourceState) throws IOException;

}
