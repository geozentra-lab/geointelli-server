package com.geointelli.ai.property.service.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.config.IngestionProperties;
import com.geointelli.ai.property.service.service.IngestionInputStore;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/** Stores inputs in S3 under one prefix per job, so any instance can run or resume the job. */
@Service
@ConditionalOnProperty(prefix = "ingestion.input", name = "storage", havingValue = "s3", matchIfMissing = true)
public class S3IngestionInputStoreImpl implements IngestionInputStore {
    private final S3Client s3Client;
    private final String bucket;
    private final String prefix;

    public S3IngestionInputStoreImpl(@Lazy S3Client s3Client, IngestionProperties properties) {
        this.s3Client = s3Client;
        this.bucket = properties.getInput().getS3Bucket();
        this.prefix = properties.getInput().getS3Prefix().replaceAll("/+$", "");
    }

    @Override
    public String storageType() { return "S3"; }

    @Override
    public String scope() { return null; }

    @Override
    public String locationFor(UUID jobId) { return prefix + "/" + jobId; }

    @Override
    public StoredInput store(String location, String name, InputStream content, long size) throws IOException {
        try {
            PutObjectResponse response = s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(key(location, name)).contentType("text/csv").contentLength(size).build(),
                RequestBody.fromInputStream(content, size));
            return new StoredInput(size, response.eTag());
        } catch (SdkException error) {
            throw new IOException("Could not store the input in S3", error);
        }
    }

    @Override
    public Optional<StoredInput> describe(String location, String name) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucket).key(key(location, name)).build());
            return Optional.of(new StoredInput(head.contentLength(), head.eTag()));
        } catch (NoSuchKeyException missing) {
            return Optional.empty();
        }
    }

    @Override
    public Path materialize(String location, String name, Path workDir) throws IOException {
        Path target = workDir.resolve(name);
        Files.deleteIfExists(target);
        try {
            s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key(location, name)).build(),
                ResponseTransformer.toFile(target));
        } catch (SdkException error) {
            throw new IOException("Could not read the input from S3", error);
        }
        return target;
    }

    @Override
    public void deleteAll(String location) {
        ListObjectsV2Response objects = s3Client.listObjectsV2(ListObjectsV2Request.builder()
            .bucket(bucket).prefix(location + "/").build());
        List<ObjectIdentifier> keys = objects.contents().stream()
            .map(object -> ObjectIdentifier.builder().key(object.key()).build()).toList();
        if (keys.isEmpty()) return;
        s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(bucket)
            .delete(Delete.builder().objects(keys).build()).build());
    }

    private String key(String location, String name) { return location + "/" + name; }
}
