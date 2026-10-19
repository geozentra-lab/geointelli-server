package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.exception.ImageStorageException;
import com.geointelli.ai.property.service.service.ImageStorageService;

/** Writes images to a folder for manual inspection during development. */
@Service("localStorage")
public class LocalImageStorageServiceImpl implements ImageStorageService {
    private final Path root;

    public LocalImageStorageServiceImpl(@Value("${images.local-directory:images}") String directory) {
        this.root = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String fileName) {
        if (!fileName.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Invalid image filename");
        }
        try {
            Files.createDirectories(root);
            Path temporary = Files.createTempFile(root, "upload-", ".tmp");
            try {
                Files.write(temporary, content);
                Files.move(temporary, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
            return root.resolve(fileName).toString();
        } catch (IOException error) {
            throw new ImageStorageException("Failed to store image locally", error);
        }
    }

}
