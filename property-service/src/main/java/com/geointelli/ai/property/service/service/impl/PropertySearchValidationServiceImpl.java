package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/** Applies the same rules before querying properties or saving a reusable search definition. */
@Service
public class PropertySearchValidationServiceImpl implements PropertySearchValidationService {
    private static final Set<String> SORT_FIELDS = Set.of(
        "id", "bedrooms", "bathrooms", "livingAreaSqft", "lotSize", "yearBuilt");
    private final Validator validator;

    public PropertySearchValidationServiceImpl(Validator validator) {
        this.validator = validator;
    }

    @Override
    public void validate(PropertySearchRequest request) {
        if (request == null) throw new IllegalArgumentException("Search filters are required");
        for (ConstraintViolation<PropertySearchRequest> violation : validator.validate(request)) {
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
        if ((long) request.getPage() * request.getSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Search offset must fit an integer");
        }
        if (request.getSortBy() == null || !SORT_FIELDS.contains(request.getSortBy())) {
            throw new IllegalArgumentException("sortBy must be id, bedrooms, bathrooms, livingAreaSqft, lotSize or yearBuilt");
        }
        if (!"asc".equalsIgnoreCase(request.getDirection()) && !"desc".equalsIgnoreCase(request.getDirection())) {
            throw new IllegalArgumentException("direction must be asc or desc");
        }
        checkRange("bedrooms", request.getMinBedrooms(), request.getMaxBedrooms());
        checkRange("bathrooms", request.getMinBathrooms(), request.getMaxBathrooms());
        checkRange("living area", request.getMinLivingAreaSqft(), request.getMaxLivingAreaSqft());
        if (request.getMinYearBuilt() != null && request.getMaxYearBuilt() != null
                && request.getMinYearBuilt() > request.getMaxYearBuilt()) {
            throw new IllegalArgumentException("minYearBuilt must not exceed maxYearBuilt");
        }
        checkRange("lot size", request.getMinLotSize(), request.getMaxLotSize());
        checkBoundingBox(request);
    }

    private void checkBoundingBox(PropertySearchRequest request) {
        long given = Stream.of(request.getMinLon(), request.getMinLat(), request.getMaxLon(), request.getMaxLat())
            .filter(Objects::nonNull).count();
        if (given == 0) return;
        if (given < 4) throw new IllegalArgumentException("minLon, minLat, maxLon and maxLat must be given together");
        if (request.getMinLon().compareTo(request.getMaxLon()) >= 0
                || request.getMinLat().compareTo(request.getMaxLat()) >= 0) {
            throw new IllegalArgumentException("Invalid bounding box: require minLon < maxLon and minLat < maxLat"
                + " (boxes crossing the antimeridian are not supported)");
        }
    }

    private void checkRange(String field, BigDecimal min, BigDecimal max) {
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)
                || (min != null && max != null && min.compareTo(max) > 0)) {
            throw new IllegalArgumentException("Invalid " + field + " range: require 0 <= minimum <= maximum");
        }
    }
}
