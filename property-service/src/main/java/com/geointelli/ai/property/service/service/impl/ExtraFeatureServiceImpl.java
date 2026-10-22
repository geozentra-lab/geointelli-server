package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.ExtraFeatureDTO;
import com.geointelli.ai.property.service.dto.PropertyExtraFeaturesDTO;
import com.geointelli.ai.property.service.entity.ExtraFeature;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.mapper.ExtraFeatureMapper;
import com.geointelli.ai.property.service.repository.ExtraFeatureRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.ExtraFeatureService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExtraFeatureServiceImpl implements ExtraFeatureService {

    private final ExtraFeatureRepository extraFeatureRepository;
    private final PropertyRepository propertyRepository;
    private final ExtraFeatureMapper extraFeatureMapper;

    @Override
    @Transactional(readOnly = true)
    public PropertyExtraFeaturesDTO getExtraFeatures(Long propertyId, Integer year, boolean history, int page, int size) {
        if (year != null && history) {
            throw new IllegalArgumentException("'year' and 'history=true' cannot be combined: ask for one year or for the full history");
        }
        if (year != null && (year < 1900 || year > 2100)) {
            throw new IllegalArgumentException("'year' must be a roll year between 1900 and 2100, got " + year);
        }
        if (!propertyRepository.existsById(propertyId)) {
            throw new PropertyNotFoundException("No property with id " + propertyId);
        }
        if (history) {
            return history(propertyId, page, size);
        }
        if (year != null) {
            return oneYear(propertyId, year);
        }
        Optional<Integer> latest = extraFeatureRepository.findMaxRollYearByPropertyId(propertyId);
        return latest.map(y -> oneYear(propertyId, y))
                     .orElseGet(() -> PropertyExtraFeaturesDTO.builder().propertyId(propertyId).features(List.of()).build());
    }

    private PropertyExtraFeaturesDTO oneYear(Long propertyId, int year) {
        List<ExtraFeatureDTO> features = extraFeatureRepository.findByProperty_IdAndRollYearOrderByIdAsc(propertyId, year)
                .stream().map(extraFeatureMapper::toDTO).toList();
        return PropertyExtraFeaturesDTO.builder().propertyId(propertyId).rollYear(year).features(features).build();
    }

    private PropertyExtraFeaturesDTO history(Long propertyId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("'page' must be 0 or more and 'size' at least 1");
        }
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        Page<ExtraFeature> rows = extraFeatureRepository.findHistoryByPropertyId(propertyId, PageRequest.of(page, boundedSize));
        return PropertyExtraFeaturesDTO.builder()
                .propertyId(propertyId)
                .history(true)
                .features(rows.getContent().stream().map(extraFeatureMapper::toDTO).toList())
                .page(rows.getNumber())
                .size(rows.getSize())
                .totalElements(rows.getTotalElements())
                .totalPages(rows.getTotalPages())
                .build();
    }
}
