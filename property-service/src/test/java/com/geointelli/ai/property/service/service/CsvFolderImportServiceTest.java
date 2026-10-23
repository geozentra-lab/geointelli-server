package com.geointelli.ai.property.service.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.geointelli.ai.property.service.service.impl.CsvFolderImportServiceImpl;

class CsvFolderImportServiceTest {
    @TempDir Path folder;
    private final CsvImportService importer = mock(CsvImportService.class);
    private final CsvFolderImportService service = new CsvFolderImportServiceImpl(importer);

    private void createFiles() throws IOException {
        for (String name : List.of("properties", "addresses", "assessments", "buildings", "owners",
                "property_owner", "lands", "sales", "taxes", "extra_features")) {
            Files.createFile(folder.resolve(name + ".csv"));
        }
    }

    @Test
    void importsInDependencyOrder() throws Exception {
        createFiles();
        service.importFolder(folder, "county", "state");
        var order = inOrder(importer);
        order.verify(importer).importProperties(folder.resolve("properties.csv"), "county", "state");
        order.verify(importer).importAddresses(folder.resolve("addresses.csv"), "county", "state");
        order.verify(importer).importAssessments(folder.resolve("assessments.csv"), "county", "state");
        order.verify(importer).importBuildings(folder.resolve("buildings.csv"), "county", "state");
        order.verify(importer).importOwners(folder.resolve("owners.csv"), folder.resolve("property_owner.csv"), "county", "state");
        order.verify(importer).importLands(folder.resolve("lands.csv"), "county", "state");
        order.verify(importer).importSales(folder.resolve("sales.csv"), "county", "state");
        order.verify(importer).importTaxes(folder.resolve("taxes.csv"), "county", "state");
        order.verify(importer).importExtraFeatures(folder.resolve("extra_features.csv"), "county", "state");
        order.verifyNoMoreInteractions();
    }

    @Test
    void importsOptionalFilesWhenPresent() throws Exception {
        createFiles();
        Files.createFile(folder.resolve("parcels.csv"));
        Files.createFile(folder.resolve("property_value_predictions.csv"));
        service.importFolder(folder, "county", "state");
        verify(importer).importParcels(folder.resolve("parcels.csv"), "county", "state");
        verify(importer).importPropertyValuePredictions(folder.resolve("property_value_predictions.csv"), "county", "state");
    }

    @Test
    void validatesAllFilesBeforeImporting() throws Exception {
        createFiles();
        Files.delete(folder.resolve("taxes.csv"));
        assertThrows(IllegalArgumentException.class, () -> service.importFolder(folder, "county", "state"));
        verifyNoInteractions(importer);
    }

    @Test
    void rejectsInvalidDirectory() {
        assertThrows(IllegalArgumentException.class,
                () -> service.importFolder(folder.resolve("missing"), "county", "state"));
        verifyNoInteractions(importer);
    }

    @Test
    void validatesOptionalFilesBeforeStartingAnyImport() throws Exception {
        createFiles();
        Files.createDirectory(folder.resolve("property_images.csv"));
        assertThrows(IllegalArgumentException.class, () -> service.importFolder(folder, "county", "state"));
        verifyNoInteractions(importer);
    }

    @Test
    void stopsWhenAnImportFails() throws Exception {
        createFiles();
        doThrow(new IOException("Import failed")).when(importer)
                .importAddresses(folder.resolve("addresses.csv"), "county", "state");
        assertThrows(IOException.class, () -> service.importFolder(folder, "county", "state"));
        verify(importer).importProperties(folder.resolve("properties.csv"), "county", "state");
        verify(importer).importAddresses(folder.resolve("addresses.csv"), "county", "state");
        verifyNoMoreInteractions(importer);
    }
}
