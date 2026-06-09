package com.geointelli.ai.property.service.service.impl;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.geointelli.ai.property.service.service.ImageStorageService;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service("s3Storage")
public class S3ImageStorageService implements ImageStorageService {
    private final String bucket; 

    private final S3Client s3Client;

    public S3ImageStorageService(@Value("${aws.s3.bucket}") String bucket, S3Client s3Client) {
        this.bucket = bucket;
        this.s3Client = s3Client;
    }

    @Override
    public String store(byte[] content, String fileName) {
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(fileName).
                contentType("image/jpeg").build(), RequestBody.fromBytes(content));
        return "https://" + bucket + ".s3.amazonaws.com/" + fileName;
    }
    
}
