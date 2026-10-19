package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.nio.file.Path;

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

}
