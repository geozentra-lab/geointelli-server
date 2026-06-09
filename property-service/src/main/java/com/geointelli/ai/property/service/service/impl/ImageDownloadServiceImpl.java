package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.geointelli.ai.property.service.exception.ImageDownloadException;
import com.geointelli.ai.property.service.service.ImageDownloadService;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class ImageDownloadServiceImpl implements ImageDownloadService {
    private final RestTemplate restTemplate;

    @Override
    public byte[] download(String imageUrl) {
        try {
            return restTemplate.getForObject(imageUrl, byte[].class);
        } 
        catch (ImageDownloadException e) {
            throw new ImageDownloadException("failed to download the image: " + imageUrl, e);
        }
    }
    
}
