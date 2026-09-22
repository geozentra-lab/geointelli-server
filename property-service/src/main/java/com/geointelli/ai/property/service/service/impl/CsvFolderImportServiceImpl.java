package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.CsvFolderImportService;
import com.geointelli.ai.property.service.service.CsvImportService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CsvFolderImportServiceImpl implements CsvFolderImportService {
    private final CsvImportService csvImportService;

    @Override
    @Transactional 
    public void importFolder(Path folderPath, String county, String sourceState) throws IOException {
        if (!Files.isDirectory(folderPath)) {
            throw new IllegalArgumentException("CSV folder does not exist or is not a directory: " + folderPath);
        }
        List<String> requiredFiles = List.of("properties.csv", "addresses.csv", "assessments.csv",
                "buildings.csv", "owners.csv", "property_owner.csv", "lands.csv", "sales.csv",
                "taxes.csv", "extra_features.csv");
        for (String filename : requiredFiles) {
            validateFile(folderPath.resolve(filename));
        }

        csvImportService.importProperties(folderPath.resolve("properties.csv"), county, sourceState);
        csvImportService.importAddresses(folderPath.resolve("addresses.csv"), county, sourceState);
        csvImportService.importAssessments(folderPath.resolve("assessments.csv"), county, sourceState);
        csvImportService.importBuildings(folderPath.resolve("buildings.csv"), county, sourceState);
        csvImportService.importOwners(folderPath.resolve("owners.csv"), folderPath.resolve("property_owner.csv"), county, sourceState);
        csvImportService.importLands(folderPath.resolve("lands.csv"), county, sourceState);
        csvImportService.importSales(folderPath.resolve("sales.csv"), county, sourceState);
        csvImportService.importTaxes(folderPath.resolve("taxes.csv"), county, sourceState);
        csvImportService.importExtraFeatures(folderPath.resolve("extra_features.csv"), county, sourceState);
    }

    private void validateFile(Path path) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalArgumentException("CSV file is missing or unreadable: " + path);
        }
    }
}
