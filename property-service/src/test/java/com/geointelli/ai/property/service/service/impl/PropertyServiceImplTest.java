package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.ChildPropertyDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PropertyServiceImplTest {

    @Mock
    PropertyRepository propertyRepository;

    @Mock
    PropertyMapper propertyMapper;

    @InjectMocks
    PropertyServiceImpl service;

    Property property;
    Property child;
    PropertyDTO propertyDTO;
    ChildPropertyDTO childPropertyDTO;

    @BeforeEach
    void setup() {

        property = new Property();
        property.setId(1L);

        child = new Property();
        child.setId(101L);
        child.setParentProperty(property);

        propertyDTO = new PropertyDTO();
        propertyDTO.setId(1L);

        childPropertyDTO = new ChildPropertyDTO(
                101L,
                null,
                null,
                null,
                1L,
                "FL",
                "Florida",
                "111111111111",
                1L,
                BigDecimal.valueOf(1),
                BigDecimal.valueOf(3),
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

    @DisplayName("Test getPropertyById method")
    @Nested
    class GetPropertyByIdTest {

        @Test
        void whenPropertyWithGivenIdExists_thenReturnProperty() {
            //Arrange
            Long propertyId = 1L;
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
            when(propertyMapper.toDTO(property)).thenReturn(propertyDTO);

            //Act
            PropertyDTO result = service.getPropertyById(propertyId);

            //Assert
            assertNotNull(result);
            assertEquals(propertyDTO.getId(), result.getId());

        }

        @Test
        void whenPropertyWithGivenIdDoesNotExist_thenThrowPropertyNotFoundException() {
            //Arrange
            Long propertyId = 1L;
            when(propertyRepository.findById(propertyId)).thenReturn(Optional.empty());

            //Assert & Act
            PropertyNotFoundException ex = assertThrows(PropertyNotFoundException.class,
                    () -> service.getPropertyById(propertyId));
            assertEquals("No property found with id: " + propertyId,
                    ex.getMessage());
            verifyNoInteractions(propertyMapper);
        }


        @Test
        void whenPropertyIdIsNull_thenThrowIllegalArgumentException() {

            //Assert & Act
            IllegalArgumentException ex =  assertThrows(IllegalArgumentException.class, () -> {
                service.getPropertyById(null);
            });


            assertEquals("id must be a positive number",
                    ex.getMessage());
            verifyNoInteractions(propertyRepository);
            verifyNoInteractions(propertyMapper);
        }


        @Test
        void whenPropertyIdIsNegative_thenThrowIllegalArgumentException() {
            //Arrange
            Long propertyId = -1L;

            //Assert & Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                service.getPropertyById(propertyId);
            });
            assertEquals("id must be a positive number",
                    ex.getMessage());
            verifyNoInteractions(propertyRepository);
            verifyNoInteractions(propertyMapper);
        }


        @Test
        void whenPropertyIdIsZero_thenThrowIllegalArgumentException() {
            //Arrange
            Long propertyId = 0L;

            //Assert & Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                service.getPropertyById(propertyId);
            });
            assertEquals("id must be a positive number",
                    ex.getMessage());
            verifyNoInteractions(propertyRepository);
            verifyNoInteractions(propertyMapper);
        }

    }

    @DisplayName("Test getChildPropertiesByParentId method")
    @Nested
    class GetChildPropertiesByParentIdTest {

        @Test
        void whenParentHasChildren_thenReturnPageOfChildren() {
            // Arrange
            Long parentId = 1L;
            PageRequest pageable = PageRequest.of(0, 10);
            when(propertyRepository.existsById(parentId)).thenReturn(true);
            when(propertyRepository.findChildPropertiesByParentId(parentId, pageable))
                    .thenReturn(new PageImpl<>(List.of(child), pageable, 1));
            when(propertyMapper.toChildDTO(child)).thenReturn(childPropertyDTO);

            // Act
            PageResponse<ChildPropertyDTO> result = service.getChildPropertiesByParentId(parentId, 0, 10);

            // Assert
            assertEquals(1, result.content().size());
            assertEquals(101L, result.content().get(0).id());
            assertEquals(parentId, result.content().get(0).parentPropertyId());
            assertEquals(0, result.page());
            assertEquals(10, result.size());
            assertEquals(1, result.totalElements());
        }

        @Test
        void whenParentDoesNotHaveChildren_thenReturnEmptyPage() {
            // Arrange
            Long parentId = 1L;
            PageRequest pageable = PageRequest.of(0, 10);
            when(propertyRepository.existsById(parentId)).thenReturn(true);
            when(propertyRepository.findChildPropertiesByParentId(parentId, pageable))
                    .thenReturn(Page.empty(pageable));

            // Act
            PageResponse<ChildPropertyDTO> result = service.getChildPropertiesByParentId(parentId, 0, 10);

            // Assert
            assertEquals(0, result.content().size());
            assertEquals(0, result.totalElements());
            verifyNoInteractions(propertyMapper);
        }

        @Test
        void whenParentDoesNotExist_thenThrowPropertyNotFoundException() {
            // Arrange
            Long parentId = 1L;
            when(propertyRepository.existsById(parentId)).thenReturn(false);

            // Act & Assert
            PropertyNotFoundException ex = assertThrows(PropertyNotFoundException.class, () -> {
                service.getChildPropertiesByParentId(parentId, 0, 10);
            });
            assertEquals("No property found with id: " + parentId, ex.getMessage());
            verifyNoInteractions(propertyMapper);
            verify(propertyRepository, never()).findChildPropertiesByParentId(any(), any());
        }

        @Test
        void whenParentIdIsNull_thenThrowIllegalArgumentException() {
            // Assert & Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                service.getChildPropertiesByParentId(null, 0, 10);
            });
            assertEquals("id must be a positive number", ex.getMessage());
            verifyNoInteractions(propertyRepository);
            verifyNoInteractions(propertyMapper);
        }

        @Test
        void whenParentIdIsNegative_thenThrowIllegalArgumentException() {
            // Assert & Act
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
                service.getChildPropertiesByParentId(-1L, 0, 10);
            });
            assertEquals("id must be a positive number", ex.getMessage());
            verifyNoInteractions(propertyRepository);
            verifyNoInteractions(propertyMapper);
        }

    }
}
