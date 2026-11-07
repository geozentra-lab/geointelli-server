package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.apache.commons.csv.CSVRecord;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.enums.IngestionDataset;

/**
 * Imports county CSV files. The whole-file methods run in one transaction; the row importers give the
 * background jobs (SCRUM-188) the same per-row logic so they can commit in batches and resume.
 */
public interface CsvImportService {
    void importPropertyImages(Path csvPath, String sourceCounty, String sourceState) throws IOException;

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

    /** Row importer for properties.csv: inserts the property and its import mapping; never overwrites. */
    Consumer<CSVRecord> propertyRowImporter(String sourceCounty, String sourceState);

    /** Whether the file has a {@code parent_property_id} column, which needs the parent-link pass. */
    boolean hasParentLinks(Path csvPath) throws IOException;

    /** Row importer of the parent-link pass over properties.csv; rows without a parent are ignored. */
    Consumer<CSVRecord> parentLinkImporter(String sourceCounty, String sourceState);

    /**
     * Row importer for a file keyed by {@code property_id}: addresses, assessments, buildings, lands, sales,
     * taxes, extra features, parcels, property value predictions.
     * @throws IllegalArgumentException for datasets without such a file (properties, owners, folder, API)
     */
    Consumer<CSVRecord> relatedRowImporter(IngestionDataset dataset, String sourceCounty, String sourceState);

    /** The property a source property ID maps to, with its county checked; same lookup the row importers use. */
    Property mappedProperty(String sourceCounty, String sourceState, Long sourcePropertyId);
}
