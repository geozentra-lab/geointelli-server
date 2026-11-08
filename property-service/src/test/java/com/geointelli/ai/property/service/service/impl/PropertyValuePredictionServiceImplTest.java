package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.geointelli.ai.property.service.dto.PropertyValuePredictionDTO;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.exception.PropertyValuePredictionNotFoundException;
import com.geointelli.ai.property.service.mapper.PropertyValuePredictionMapper;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;

class PropertyValuePredictionServiceImplTest {
    private PropertyValuePredictionRepository repository;
    private PropertyValuePredictionServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(PropertyValuePredictionRepository.class);
        PropertyValuePredictionMapper mapper = mock(PropertyValuePredictionMapper.class);
        when(mapper.toDTO(any())).thenAnswer(invocation -> {
            PropertyValuePrediction source = invocation.getArgument(0);
            PropertyValuePredictionDTO dto = new PropertyValuePredictionDTO();
            dto.setPropertyId(source.getPropertyId());
            dto.setPredictedPrice(source.getPredictedPrice());
            dto.setLastSalePrice(source.getLastSalePrice());
            return dto;
        });
        service = new PropertyValuePredictionServiceImpl(repository, mapper);
    }

    private static PropertyValuePrediction prediction(long propertyId, String status, String predicted, String forecast1y) {
        PropertyValuePrediction p = new PropertyValuePrediction();
        p.setPropertyId(propertyId);
        p.setValuationStatus(status);
        p.setPredictedPrice(predicted == null ? null : new BigDecimal(predicted));
        p.setForecast1y(forecast1y == null ? null : new BigDecimal(forecast1y));
        p.setLastSalePrice(new BigDecimal("100000"));
        return p;
    }

    @ParameterizedTest
    @CsvSource({
        "supported, 800000, 840000, 5.0000",
        "limited, 800000, 760000, -5.0000",
        "supported, 800000, 800000, 0.0000",
        "supported, 300000, 310000, 3.3333"
    })
    @DisplayName("When the valuation is supported or limited, then growth is the projected 1-year change from the predicted price")
    void whenSupported_thenOneYearChange(String status, String predicted, String forecast, String expected) throws Exception {
        when(repository.findByPropertyId(1L)).thenReturn(Optional.of(prediction(1L, status, predicted, forecast)));

        assertThat(service.getPropertyValuePredictionById(1L).getGrowthPercentage()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("When there is a last sale price, then growth does not use it")
    void whenLastSale_thenNotUsed() throws Exception {
        PropertyValuePrediction p = prediction(1L, "supported", "800000", "840000");
        p.setLastSalePrice(new BigDecimal("400000"));
        when(repository.findByPropertyId(1L)).thenReturn(Optional.of(p));

        assertThat(service.getPropertyValuePredictionById(1L).getGrowthPercentage()).isEqualTo(new BigDecimal("5.0000"));
    }

    @ParameterizedTest
    @CsvSource(value = {
        "unsupported, 800000, 840000",
        "NULL, 800000, 840000",
        "experimental, 800000, 840000",
        "supported, NULL, 840000",
        "supported, 0, 840000",
        "supported, -1, 840000",
        "supported, 800000, NULL",
        "supported, 800000, 0"
    }, nullValues = "NULL")
    @DisplayName("When the valuation is unsupported or an input is missing or not positive, then growth is null, not zero")
    void whenUnavailable_thenNull(String status, String predicted, String forecast) throws Exception {
        when(repository.findByPropertyId(1L)).thenReturn(Optional.of(prediction(1L, status, predicted, forecast)));

        assertThat(service.getPropertyValuePredictionById(1L).getGrowthPercentage()).isNull();
    }

    @Test
    @DisplayName("When the property has no prediction, then PropertyValuePredictionNotFoundException is thrown")
    void whenMissing_thenNotFound() {
        when(repository.findByPropertyId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPropertyValuePredictionById(1L))
            .isInstanceOf(PropertyValuePredictionNotFoundException.class);
    }

    @Test
    @DisplayName("When several IDs are requested, then duplicates are removed and each growth is computed on its own")
    void whenSeveralIds_thenEachComputed() throws Exception {
        when(repository.findByPropertyIdIn(List.of(1L, 2L))).thenReturn(List.of(
            prediction(1L, "supported", "800000", "840000"),
            prediction(2L, "unsupported", "500000", "520000")));

        List<PropertyValuePredictionDTO> result = service.getPropertyValuePredictionByIds(List.of(1L, 2L, 1L));

        verify(repository).findByPropertyIdIn(List.of(1L, 2L));
        assertThat(result).extracting(PropertyValuePredictionDTO::getPropertyId).containsExactly(1L, 2L);
        assertThat(result.get(0).getGrowthPercentage()).isEqualTo(new BigDecimal("5.0000"));
        assertThat(result.get(1).getGrowthPercentage()).isNull();
    }

    @Test
    @DisplayName("When none of the IDs has a prediction, then PropertyValuePredictionNotFoundException is thrown")
    void whenNoneFound_thenNotFound() {
        when(repository.findByPropertyIdIn(List.of(9L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.getPropertyValuePredictionByIds(List.of(9L)))
            .isInstanceOf(PropertyValuePredictionNotFoundException.class);
    }
}
