package com.geointelli.ai.property.service.controller;

import com.geointelli.ai.property.service.dto.ChildPropertyDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.PropertyService;
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
}
