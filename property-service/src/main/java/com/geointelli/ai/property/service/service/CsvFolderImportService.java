package com.geointelli.ai.property.service.service;

import java.io.IOException;
import java.nio.file.Path;

public interface CsvFolderImportService {
    void importFolder(Path folderPath, String county, String sourceState) throws IOException;
}
