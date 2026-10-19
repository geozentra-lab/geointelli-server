package com.geointelli.ai.property.service.service;

import java.nio.file.Path;

public interface PropertyImageImportService {
    void importCsv(Path csvFile);

    void importFolder(Path folder);
}
