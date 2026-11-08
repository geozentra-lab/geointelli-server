package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.ChildPropertyDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.ExtraFeatureService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.PropertyService;
import com.geointelli.ai.property.service.service.ValuationService;
import com.geointelli.ai.property.service.dto.ComparableResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse;
import com.geointelli.ai.property.service.dto.ValuationResponse.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;


import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PropertyController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PropertyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PropertyService propertyService;

    @MockBean
    PropertySearchService propertySearchService;

    @MockBean
    ExtraFeatureService extraFeatureService;

    @MockBean
    ValuationService valuationService;

    PropertyDTO propertyDTO;

    ChildPropertyDTO childPropertyDTO;

    @BeforeEach
    void setUp() {
        propertyDTO = new PropertyDTO();
        propertyDTO.setId(1L);


        childPropertyDTO = new ChildPropertyDTO(
                101L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                1L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Nested
    @DisplayName("Get Property By ID")
    class GetPropertyByIdTest {

        @Test
        void whenPropertyExists_thenReturnProperty200() throws Exception {

            //Arrange
            Long propertyId = 1L;
            when(propertyService.getPropertyById(propertyId)).thenReturn(propertyDTO);

            //Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}", propertyId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(propertyId));
            verify(propertyService).getPropertyById(1L);

        }

        @Test
        void whenPropertyDoesNotExist_thenReturnNotFound404() throws Exception {
            //Arrange
            Long propertyId = 1L;
            when(propertyService.getPropertyById(propertyId))
                    .thenThrow(new PropertyNotFoundException("No property found with id: " + propertyId));

            //Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}", 1L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("No property found with id: " + propertyId));

            verify(propertyService).getPropertyById(1L);
        }

        @Test
        void whenInvalidId_thenReturnBadRequest400() throws Exception {
            //Arrange
            String invalidId = "abc";

            //Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}", invalidId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("id: invalid value"));

            verifyNoInteractions(propertyService);
        }

        @Test
        void whenNegativeId_thenReturnBadRequest400() throws Exception {
            //Arrange
            Long negativeId = -1L;
            when(propertyService.getPropertyById(negativeId))
                    .thenThrow(new IllegalArgumentException("id must be a positive number"));

            //Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}", negativeId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("id must be a positive number"));

            verify(propertyService).getPropertyById(negativeId);
        }
    }

    @Nested
    @DisplayName("Get Child Properties By Parent ID")
    class GetChildPropertiesTest{

        @Test
        void whenParentPropertyExists_thenReturnChildProperties200() throws Exception {
            //Arrange
            Long parentId = 1L;
            PageResponse<ChildPropertyDTO> pageResponse = new PageResponse<>(
                    List.of(childPropertyDTO), // content
                    0,                         // page
                    20,                        // size
                    1L,                        // totalElements
                    1,                         // totalPages
                    true,                       // first
                    true                        // last
            );
            when(propertyService.getChildPropertiesByParentId(parentId, 0, 20))
                    .thenReturn(pageResponse);

            //Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}/children", parentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(101))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].parentPropertyId").value(1))
                    .andExpect(jsonPath("$.content[0].parentProperty").doesNotExist());
            verify(propertyService).getChildPropertiesByParentId(1L, 0, 20);
        }

        @Test
        void whenPageAndSizeGiven_thenPassThemToService() throws Exception {
            // Arrange
            Long parentId = 1L;
            when(propertyService.getChildPropertiesByParentId(parentId, 2, 5))
                    .thenReturn(new PageResponse<>(List.of(), 2, 5, 0L, 0, false, true));

            // Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}/children", parentId)
                            .param("page", "2")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(2))
                    .andExpect(jsonPath("$.size").value(5));
            verify(propertyService).getChildPropertiesByParentId(parentId, 2, 5);
        }


        @Test
        void whenParentHasNoChildren_thenReturnEmptyPage200() throws Exception {
            // Arrange
            Long parentId = 1L;
            when(propertyService.getChildPropertiesByParentId(parentId, 0, 20))
                    .thenReturn(new PageResponse<>(List.of(), 0, 20, 0L, 0, true, true));

            // Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}/children", parentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        void whenParentDoesNotExist_thenReturnNotFound404() throws Exception {
            // Arrange
            Long parentId = 999L;
            when(propertyService.getChildPropertiesByParentId(parentId, 0, 20))
                    .thenThrow(new PropertyNotFoundException("No property found with id: " + parentId));

            // Act & Assert
            mockMvc.perform(get("/api/properties/id/{id}/children", parentId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("No property found with id: " + parentId));
        }

        @Test
        void whenParentIdNotNumeric_thenReturnBadRequest400() throws Exception {
            mockMvc.perform(get("/api/properties/id/{id}/children", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("id: invalid value"));
            verifyNoInteractions(propertyService);
        }


        @ParameterizedTest(name = "{0}={1} -> 400")
        @CsvSource({"size,0", "size,101", "page,-1", "size,abc", "page,abc"})
        void whenPagingInvalid_thenReturnBadRequest400(String param, String value) throws Exception {
            mockMvc.perform(get("/api/properties/id/{id}/children", 1L).param(param, value))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"));
            verifyNoInteractions(propertyService);
        }
    }

    @Nested
    @DisplayName("Get Valuation")
    class GetValuationTest {

        private ValuationResponse supported() {
            ComparableResponse comparables = new ComparableResponse();
            comparables.setStatus("COMPARABLES_FOUND");
            return new ValuationResponse("supported", List.of(),
                    new Estimate(new BigDecimal("822855.47"), new Range(new BigDecimal("676666.91"), new BigDecimal("1000626.92")),
                            new Range(new BigDecimal("612063.54"), new BigDecimal("1106243.20")), "high"),
                    new Forecasts(new Projection(new BigDecimal("844881.25"), null, null), null, null),
                    new Explanation(new BigDecimal("755227"), "summary",
                            List.of(new Factor("living_area", "Living area", new BigDecimal("12.345"), new BigDecimal("90123.40"),
                                    "Its living area raises the value by about 12%.")), "note"),
                    new Evidence(new LastSale(true, new BigDecimal("610000.00"), LocalDate.of(2019, 5, 2)),
                            new SupportingComparables("label", "COMPARABLES_FOUND", comparables)),
                    new Method("gbm_hedonic", "2026.10", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 30),
                            LocalDateTime.of(2026, 10, 2, 3, 0)),
                    new Reproducibility("run-20261001", "abcdef0123456789"), List.of("Stored model note."));
        }

        @Test
        @DisplayName("When the valuation is supported, then 200 returns the stored estimate, explanation, evidence and method")
        void whenSupported_thenReturn200() throws Exception {
            when(valuationService.getValuation(95340L)).thenReturn(supported());

            mockMvc.perform(get("/api/properties/id/{id}/valuation", 95340L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("supported"))
                    .andExpect(jsonPath("$.estimate.value").value(822855.47))
                    .andExpect(jsonPath("$.estimate.range80.low").value(676666.91))
                    .andExpect(jsonPath("$.estimate.range90.high").value(1106243.20))
                    .andExpect(jsonPath("$.estimate.confidence").value("high"))
                    .andExpect(jsonPath("$.forecasts.oneYear.value").value(844881.25))
                    .andExpect(jsonPath("$.explanation.factors[0].code").value("living_area"))
                    .andExpect(jsonPath("$.explanation.factors[0].pct").value(12.345))
                    .andExpect(jsonPath("$.explanation.factors[0].text").value("Its living area raises the value by about 12%."))
                    .andExpect(jsonPath("$.evidence.lastSale.date").value("2019-05-02"))
                    .andExpect(jsonPath("$.evidence.comparables.status").value("COMPARABLES_FOUND"))
                    .andExpect(jsonPath("$.method.predictionDate").value("2026-10-01"))
                    .andExpect(jsonPath("$.reproducibility.runId").value("run-20261001"))
                    .andExpect(jsonPath("$.notes[0]").value("Stored model note."));
            verify(valuationService).getValuation(95340L);
        }

        @Test
        @DisplayName("When the valuation is unsupported, then 200 returns status and reasons and omits the empty sections")
        void whenUnsupported_thenReturn200WithoutEmptySections() throws Exception {
            when(valuationService.getValuation(7L)).thenReturn(new ValuationResponse("unsupported",
                    List.of(new Reason("not_modelled", "This property type is not covered by the model yet.")),
                    null, null, null, null, null, null, null));

            mockMvc.perform(get("/api/properties/id/{id}/valuation", 7L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("unsupported"))
                    .andExpect(jsonPath("$.reasons[0].code").value("not_modelled"))
                    .andExpect(jsonPath("$.reasons[0].message").value("This property type is not covered by the model yet."))
                    .andExpect(jsonPath("$.estimate").doesNotExist())
                    .andExpect(jsonPath("$.forecasts").doesNotExist())
                    .andExpect(jsonPath("$.explanation").doesNotExist())
                    .andExpect(jsonPath("$.evidence").doesNotExist())
                    .andExpect(jsonPath("$.method").doesNotExist())
                    .andExpect(jsonPath("$.reproducibility").doesNotExist())
                    .andExpect(jsonPath("$.notes").doesNotExist());
        }

        @Test
        @DisplayName("When the property does not exist, then 404")
        void whenPropertyMissing_thenReturn404() throws Exception {
            when(valuationService.getValuation(1L)).thenThrow(new PropertyNotFoundException("Property not found: 1"));

            mockMvc.perform(get("/api/properties/id/{id}/valuation", 1L))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("Not Found"))
                    .andExpect(jsonPath("$.message").value("Property not found: 1"));
        }

        @Test
        @DisplayName("When the ID is not positive, then 400")
        void whenIdNotPositive_thenReturn400() throws Exception {
            when(valuationService.getValuation(0L)).thenThrow(new IllegalArgumentException("propertyId must be positive"));

            mockMvc.perform(get("/api/properties/id/{id}/valuation", 0L))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Bad Request"))
                    .andExpect(jsonPath("$.message").value("propertyId must be positive"));
        }

        @Test
        @DisplayName("When the ID is not numeric, then 400 without calling the service")
        void whenIdNotNumeric_thenReturn400() throws Exception {
            mockMvc.perform(get("/api/properties/id/{id}/valuation", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("id: invalid value"));
            verifyNoInteractions(valuationService);
        }
    }
}
