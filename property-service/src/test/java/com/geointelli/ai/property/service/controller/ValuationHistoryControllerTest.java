package com.geointelli.ai.property.service.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.geointelli.ai.property.service.advice.GlobalExceptionHandler;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.ValuationHistoryReader;
import com.geointelli.ai.property.service.service.ValuationService;
import com.geointelli.ai.property.service.service.impl.ValuationHistoryServiceImpl;

/** Uses real service validation and pagination with an isolated history reader. */
class ValuationHistoryControllerTest {
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        when(properties.existsById(7L)).thenReturn(true);
        var reader = mock(ValuationHistoryReader.class);
        when(reader.findByPropertyId(7L, PageRequest.of(0,20))).thenReturn(Page.empty(PageRequest.of(0,20)));
        var service = new ValuationHistoryServiceImpl(properties, reader, mock(ValuationService.class));
        mvc = MockMvcBuilders.standaloneSetup(new ValuationHistoryController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void propertyWithoutRecordedVersionsReturnsEmptyHistory() throws Exception {
        mvc.perform(get("/api/properties/id/7/valuations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isEmpty())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void unknownPropertyReturns404() throws Exception {
        mvc.perform(get("/api/properties/id/8/valuations")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "abc"})
    void invalidPageSizeReturns400(String size) throws Exception {
        mvc.perform(get("/api/properties/id/7/valuations").param("size", size)).andExpect(status().isBadRequest());
        verifyNoInteractions(properties);
    }
}
