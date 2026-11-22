package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.geointelli.ai.property.service.config.IngestionProperties;

/** The scope recorded on local-input jobs must be the one this instance's worker claims jobs with. */
class LocalIngestionInputStoreImplTest {
    @TempDir Path directory;

    private IngestionProperties properties(String instanceId) {
        IngestionProperties properties = new IngestionProperties();
        properties.getInput().setLocalDirectory(directory.toString());
        properties.getJobs().setInstanceId(instanceId);
        return properties;
    }

    @Test
    @DisplayName("Without INGESTION_INSTANCE_ID the scope is the worker's resolved ID (host name), never empty")
    void blankInstanceIdUsesResolvedId() {
        IngestionProperties properties = properties("");
        String scope = new LocalIngestionInputStoreImpl(properties).scope();
        assertFalse(scope.isBlank());
        assertEquals(properties.getJobs().resolvedInstanceId(), scope);
    }

    @Test
    @DisplayName("A configured instance ID is used as given")
    void configuredInstanceId() {
        assertEquals("local-dev", new LocalIngestionInputStoreImpl(properties(" local-dev ")).scope());
    }
}
