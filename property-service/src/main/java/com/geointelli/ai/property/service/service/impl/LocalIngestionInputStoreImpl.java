package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.service.IngestionInputStore;

/**
 * Stores inputs in a directory of this instance. Jobs created here carry the instance ID as their scope,
 * so only this instance (or a restarted one with the same ID and directory) runs or retries them.
 */
@Service
@ConditionalOnProperty(prefix = "ingestion.input", name = "storage", havingValue = "local")
public class LocalIngestionInputStoreImpl implements IngestionInputStore {
    private final Path baseDirectory;
    private final String instanceId;

    public LocalIngestionInputStoreImpl(IngestionProperties properties) {
        this.baseDirectory = Path.of(properties.getInput().getLocalDirectory()).toAbsolutePath().normalize();
        this.instanceId = properties.getJobs().getInstanceId();
    }

    @Override
    public String storageType() { return "LOCAL"; }

    @Override
    public String scope() { return instanceId; }

    @Override
    public String locationFor(UUID jobId) { return jobId.toString(); }

    @Override
    public StoredInput store(String location, String name, InputStream content, long size) throws IOException {
        Path target = file(location, name);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return describe(location, name).orElseThrow(() -> new IOException("Stored input is not readable"));
    }

    @Override
    public Optional<StoredInput> describe(String location, String name) {
        Path file = file(location, name);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return Optional.of(new StoredInput(attributes.size(),
                attributes.size() + "-" + attributes.lastModifiedTime().toMillis()));
        } catch (IOException error) {
            return Optional.empty();
        }
    }

    @Override
    public Path materialize(String location, String name, Path workDir) throws IOException {
        Path file = file(location, name);
        if (!Files.isReadable(file)) throw new IOException("Stored input is missing: " + name);
        return file;
    }

    @Override
    public void deleteAll(String location) {
        Path directory = baseDirectory.resolve(location).normalize();
        if (!directory.startsWith(baseDirectory) || !Files.isDirectory(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover directory only wastes disk space; it is reported by the next describe().
        }
    }

    private Path file(String location, String name) {
        Path file = baseDirectory.resolve(location).resolve(name).normalize();
        if (!file.startsWith(baseDirectory)) throw new IllegalArgumentException("Invalid input location");
        return file;
    }
}
