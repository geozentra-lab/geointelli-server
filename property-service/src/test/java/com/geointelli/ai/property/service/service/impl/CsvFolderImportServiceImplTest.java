package com.geointelli.ai.property.service.service.impl;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.geointelli.ai.property.service.service.CsvImportService;

class CsvFolderImportServiceImplTest {
    @TempDir Path folder;

    private void createCountyFiles() throws Exception {
        for (String name : List.of("properties.csv", "addresses.csv", "assessments.csv", "buildings.csv",
                "owners.csv", "property_owner.csv", "lands.csv", "sales.csv", "taxes.csv", "extra_features.csv")) {
            Files.createFile(folder.resolve(name));
        }
    }

    @Test
    void importsImagesAfterPropertyData() throws Exception {
        createCountyFiles();
        Files.createFile(folder.resolve("property_images.csv"));
        CsvImportService imports = mock(CsvImportService.class);
        new CsvFolderImportServiceImpl(imports).importFolder(folder, "Baker", "FL");
        var order = inOrder(imports);
        order.verify(imports).importProperties(folder.resolve("properties.csv"), "Baker", "FL");
        order.verify(imports).importExtraFeatures(folder.resolve("extra_features.csv"), "Baker", "FL");
        order.verify(imports).importPropertyImages(folder.resolve("property_images.csv"), "Baker", "FL");
    }

    @Test
    void supportsFoldersWithoutImages() throws Exception {
        createCountyFiles();
        CsvImportService imports = mock(CsvImportService.class);
        new CsvFolderImportServiceImpl(imports).importFolder(folder, "Baker", "FL");
        verify(imports, never()).importPropertyImages(any(), anyString(), anyString());
    }
}
