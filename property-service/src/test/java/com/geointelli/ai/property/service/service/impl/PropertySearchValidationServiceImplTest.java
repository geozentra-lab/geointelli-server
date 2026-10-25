package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

/** Ensures saved definitions and direct searches use the same field, range and geography validation. */
class PropertySearchValidationServiceImplTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final PropertySearchValidationServiceImpl validation = new PropertySearchValidationServiceImpl(FACTORY.getValidator());

    @AfterAll
    static void cleanup() { FACTORY.close(); }

    @Test
    void emptyFiltersAreAValidAllPropertiesSearch() {
        assertDoesNotThrow(() -> validation.validate(new PropertySearchRequest()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "bedrooms", "bathrooms", "livingAreaSqft", "lotSize", "yearBuilt"})
    void acceptsTheExistingSortFields(String field) {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setSortBy(field);
        request.setDirection("DESC");
        assertDoesNotThrow(() -> validation.validate(request));
    }

    @Test
    void rejectsUnsupportedSortingAndNullRequest() {
        assertThrows(IllegalArgumentException.class, () -> validation.validate(null));
        PropertySearchRequest request = new PropertySearchRequest();
        request.setSortBy("unknown");
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setSortBy("id");
        request.setDirection("invalid");
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
    }

    @Test
    void rejectsInvalidBeanFieldsAndPagingIncludingOverflow() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setCountyId(-1L);
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setCountyId(null);
        request.setState("s".repeat(101));
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setState(null);
        request.setSize(101);
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setSize(100);
        request.setPage(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
    }

    @Test
    void rejectsReversedAndNegativeRanges() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setMinBedrooms(BigDecimal.valueOf(4));
        request.setMaxBedrooms(BigDecimal.valueOf(3));
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setMaxBedrooms(null);
        request.setMinBedrooms(BigDecimal.valueOf(-1));
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setMinBedrooms(null);
        request.setMinYearBuilt(2020);
        request.setMaxYearBuilt(1990);
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
    }

    @Test
    void boundingBoxRequiresAllCoordinatesAndValidBounds() {
        PropertySearchRequest request = new PropertySearchRequest();
        request.setMinLon(new BigDecimal("-80"));
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setMaxLon(new BigDecimal("-79"));
        request.setMinLat(new BigDecimal("25"));
        request.setMaxLat(new BigDecimal("26"));
        assertDoesNotThrow(() -> validation.validate(request));
        request.setMaxLon(new BigDecimal("-80"));
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
        request.setMaxLon(new BigDecimal("181"));
        assertThrows(IllegalArgumentException.class, () -> validation.validate(request));
    }

    @Test
    void ordinarySearchValidatesBeforeTouchingTheDatabase() {
        EntityManager entityManager = mock(EntityManager.class);
        PropertySearchServiceImpl service = new PropertySearchServiceImpl(entityManager, validation);
        PropertySearchRequest request = new PropertySearchRequest();
        request.setMinLotSize(BigDecimal.TEN);
        request.setMaxLotSize(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class, () -> service.search(request));
        verifyNoInteractions(entityManager);
    }
}
