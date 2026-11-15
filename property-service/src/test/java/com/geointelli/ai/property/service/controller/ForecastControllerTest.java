package com.geointelli.ai.property.service.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.PropertyValuePredictionRepository;
import com.geointelli.ai.property.service.service.ComparableService;
import com.geointelli.ai.property.service.service.impl.ForecastServiceImpl;
import com.geointelli.ai.property.service.service.impl.ValuationServiceImpl;

/**
 * Exercises the controller and both real display services against isolated
 * repositories.
 */
class ForecastControllerTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PropertyValuePredictionRepository predictions = mock(PropertyValuePredictionRepository.class);
    private final ComparableService comparables = mock(ComparableService.class);
    private PropertyValuePrediction prediction;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(properties.existsById(7L)).thenReturn(true);
        prediction = new PropertyValuePrediction();
        prediction.setPropertyId(7L);
        prediction.setValuationStatus("supported");
        prediction.setStatusReasons("[]");
        prediction.setNotes("[]");
        prediction.setConfidence("low");
        prediction.setPredictedPrice(new BigDecimal("671183.25"));
        prediction.setForecast1y(new BigDecimal("700001.23"));
        prediction.setForecast1yLow(new BigDecimal("550000.12"));
        prediction.setForecast1yHigh(new BigDecimal("850000.34"));
        prediction.setForecast7y(new BigDecimal("900002.35"));
        prediction.setModelVersion("monthly-v1");
        when(predictions.findByPropertyId(7L)).thenReturn(Optional.of(prediction));
        var valuation = new ValuationServiceImpl(properties, predictions, comparables, new ObjectMapper());
        mvc = MockMvcBuilders.standaloneSetup(new ForecastController(new ForecastServiceImpl(valuation)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void preservesStoredNumbersAndLowConfidenceWithoutComparableSearch() throws Exception {
        mvc.perform(get("/api/properties/id/7/forecast"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.confidence").value("low"))
                .andExpect(jsonPath("$.forecasts.oneYear.value").value(700001.23))
                .andExpect(jsonPath("$.forecasts.oneYear.low").value(550000.12))
                .andExpect(jsonPath("$.forecasts.oneYear.high").value(850000.34))
                .andExpect(jsonPath("$.forecasts.sevenYears.value").value(900002.35))
                .andExpect(jsonPath("$.forecasts.threeYears.value").isEmpty())
                .andExpect(jsonPath("$.method.modelVersion").value("monthly-v1"))
                .andExpect(jsonPath("$.evidence").doesNotExist());
        verifyNoInteractions(comparables);
    }

    @Test
    void limitedRetainsCaveat() throws Exception {
        prediction.setValuationStatus("limited");
        prediction.setStatusReasons("[\"unknown_future_reason\"]");
        mvc.perform(get("/api/properties/id/7/forecast"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("limited"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reasons[0].code").value("unknown_future_reason"));
    }

    @Test
    void unsupportedSuppressesEvenStoredForecasts() throws Exception {
        prediction.setValuationStatus("unsupported");
        mvc.perform(get("/api/properties/id/7/forecast"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.forecasts").doesNotExist());
    }

    @Test
    void missingForecastIsExplicitWithoutChangingValuationStatus() throws Exception {
        prediction.setForecast1y(null);
        mvc.perform(get("/api/properties/id/7/forecast"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("supported"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasons[0].code").value("forecast_unavailable"))
                .andExpect(jsonPath("$.forecasts").doesNotExist());
    }

    @Test
    void unmodelledReturns200() throws Exception {
        when(predictions.findByPropertyId(7L)).thenReturn(Optional.empty());
        mvc.perform(get("/api/properties/id/7/forecast"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("unsupported"))
                .andExpect(jsonPath("$.reasons[0].code").value("not_modelled"));
    }

    @Test
    void nonexistentPropertyReturns404() throws Exception {
        mvc.perform(get("/api/properties/id/8/forecast")).andExpect(status().isNotFound());
    }

    @Test
    void invalidIdReturns400() throws Exception {
        mvc.perform(get("/api/properties/id/0/forecast")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/properties/id/abc/forecast")).andExpect(status().isBadRequest());
        verifyNoInteractions(predictions, comparables);
    }
}
