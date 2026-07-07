package com.geointelli.ai.property.service.service.impl;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            headers.set("Referer", "https://www.realtor.com/");
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(imageUrl, HttpMethod.GET, entity, byte[].class);
            return response.getBody();
        } 
        catch (HttpClientErrorException.Forbidden e) {
            throw new ImageDownloadException("403 Forbidden: " + imageUrl);
        } 
        catch (Exception e) {
            throw new ImageDownloadException("Failed to download image: " + imageUrl);
        }
    }
}
