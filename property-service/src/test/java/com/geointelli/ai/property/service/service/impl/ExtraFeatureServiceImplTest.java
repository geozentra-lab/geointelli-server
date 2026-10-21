package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.geointelli.ai.property.service.dto.ExtraFeatureDTO;
import com.geointelli.ai.property.service.dto.PropertyExtraFeaturesDTO;
import com.geointelli.ai.property.service.entity.ExtraFeature;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.mapper.ExtraFeatureMapper;
import com.geointelli.ai.property.service.repository.ExtraFeatureRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;

class ExtraFeatureServiceImplTest {

    private static final long PROPERTY = 1L;

    private ExtraFeatureRepository extraFeatureRepository;
    private PropertyRepository propertyRepository;
    private ExtraFeatureServiceImpl service;

    @BeforeEach
    void setUp() {
        extraFeatureRepository = mock(ExtraFeatureRepository.class);
        propertyRepository = mock(PropertyRepository.class);
        ExtraFeatureMapper mapper = mock(ExtraFeatureMapper.class);
        when(mapper.toDTO(any())).thenAnswer(call -> {
            ExtraFeature e = call.getArgument(0);
            ExtraFeatureDTO dto = new ExtraFeatureDTO();
            dto.setRollYear(e.getRollYear());
            dto.setDescription(e.getDescription());
            return dto;
        });
        when(propertyRepository.existsById(PROPERTY)).thenReturn(true);
        service = new ExtraFeatureServiceImpl(extraFeatureRepository, propertyRepository, mapper);
    }

    private static ExtraFeature feature(long id, Integer rollYear, String description) {
        ExtraFeature e = new ExtraFeature();
        e.setId(id);
        e.setRollYear(rollYear);
        e.setDescription(description);
        return e;
    }

    @Test
    void defaultRequestReturnsEveryFeatureOfTheLatestRollYear() {
        when(extraFeatureRepository.findMaxRollYearByPropertyId(PROPERTY)).thenReturn(Optional.of(2026));
        when(extraFeatureRepository.findByProperty_IdAndRollYearOrderByIdAsc(PROPERTY, 2026))
            .thenReturn(List.of(feature(10, 2026, "POOL"), feature(11, 2026, "DECK"), feature(12, 2026, "FENCE")));

        PropertyExtraFeaturesDTO out = service.getExtraFeatures(PROPERTY, null, false, 0, 50);

        assertEquals(2026, out.getRollYear());
        assertFalse(out.isHistory());
        assertEquals(List.of("POOL", "DECK", "FENCE"), out.getFeatures().stream().map(ExtraFeatureDTO::getDescription).toList());
        assertNull(out.getTotalElements());
        verify(extraFeatureRepository, never()).findHistoryByPropertyId(anyLong(), any());
    }

    @Test
    void defaultRequestOnAPropertyWithoutDatedFeaturesIsEmptyNotAnError() {
        when(extraFeatureRepository.findMaxRollYearByPropertyId(PROPERTY)).thenReturn(Optional.empty());

        PropertyExtraFeaturesDTO out = service.getExtraFeatures(PROPERTY, null, false, 0, 50);

        assertEquals(PROPERTY, out.getPropertyId());
        assertNull(out.getRollYear());
        assertTrue(out.getFeatures().isEmpty());
        verify(extraFeatureRepository, never()).findByProperty_IdAndRollYearOrderByIdAsc(anyLong(), anyInt());
    }

    @Test
    void explicitYearReturnsThatYearOnlyWithoutLookingUpTheLatest() {
        when(extraFeatureRepository.findByProperty_IdAndRollYearOrderByIdAsc(PROPERTY, 2024))
            .thenReturn(List.of(feature(1, 2024, "SHED")));

        PropertyExtraFeaturesDTO out = service.getExtraFeatures(PROPERTY, 2024, false, 0, 50);

        assertEquals(2024, out.getRollYear());
        assertEquals(1, out.getFeatures().size());
        assertEquals("SHED", out.getFeatures().get(0).getDescription());
        verify(extraFeatureRepository, never()).findMaxRollYearByPropertyId(anyLong());
    }

    @Test
    void historyIsPagedKeepsTheRepositoryOrderAndCarriesEachFeaturesYear() {
        List<ExtraFeature> page = List.of(feature(12, 2026, "FENCE"), feature(5, 2025, "POOL"), feature(1, 2024, "SHED"), feature(99, null, "OLD"));
        when(extraFeatureRepository.findHistoryByPropertyId(eq(PROPERTY), any(Pageable.class)))
            .thenReturn(new PageImpl<>(page, PageRequest.of(0, 4), 9));

        PropertyExtraFeaturesDTO out = service.getExtraFeatures(PROPERTY, null, true, 0, 4);

        assertTrue(out.isHistory());
        assertNull(out.getRollYear());
        assertEquals(Arrays.asList(2026, 2025, 2024, null), out.getFeatures().stream().map(ExtraFeatureDTO::getRollYear).toList());
        assertEquals(0, out.getPage());
        assertEquals(4, out.getSize());
        assertEquals(9L, out.getTotalElements());
        assertEquals(3, out.getTotalPages());
    }

    @Test
    void historyPageSizeIsCapped() {
        when(extraFeatureRepository.findHistoryByPropertyId(eq(PROPERTY), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 200), 0));

        service.getExtraFeatures(PROPERTY, null, true, 0, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(extraFeatureRepository).findHistoryByPropertyId(eq(PROPERTY), pageable.capture());
        assertEquals(200, pageable.getValue().getPageSize());
    }

    @Test
    void unknownPropertyIsNotFound() {
        when(propertyRepository.existsById(404L)).thenReturn(false);

        assertThrows(PropertyNotFoundException.class, () -> service.getExtraFeatures(404L, null, false, 0, 50));
        verifyNoInteractions(extraFeatureRepository);
    }

    @Test
    void yearAndHistoryTogetherAreRejectedBeforeAnythingElse() {
        assertThrows(IllegalArgumentException.class, () -> service.getExtraFeatures(PROPERTY, 2024, true, 0, 50));
        verifyNoInteractions(extraFeatureRepository);
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void invalidYearAndInvalidPagingAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.getExtraFeatures(PROPERTY, 1800, false, 0, 50));
        assertThrows(IllegalArgumentException.class, () -> service.getExtraFeatures(PROPERTY, null, true, -1, 50));
        assertThrows(IllegalArgumentException.class, () -> service.getExtraFeatures(PROPERTY, null, true, 0, 0));
        verifyNoInteractions(extraFeatureRepository);
    }
}
