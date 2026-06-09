package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.exception.ImageStorageException;
import com.geointelli.ai.property.service.service.ImageStorageService;

import lombok.extern.slf4j.Slf4j;

@Service("localStorage")
@Slf4j
public class LocalImageStorageServiceImpl implements ImageStorageService {
    private final Path root = Paths.get("images");

    @Override
    public String store(byte[] content, String fileName) {
        try{
            Files.createDirectories(root);
            Path filePath = root.resolve(fileName);
            log.info("Saving image to {}", filePath.toAbsolutePath());
            Files.write(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return filePath.toString();
        }
        catch (IOException e) {
            throw new ImageStorageException("Failed to store image locally: " + fileName, e);
        }
    }
    
}
