package com.geointelli.ai.property.service.service.impl;

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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PropertyServiceImplTest {

    @Mock
    PropertyRepository propertyRepository;

    @Mock
    PropertyMapper propertyMapper;

    @InjectMocks
    PropertyServiceImpl service;

    Property property;
    PropertyDTO propertyDTO;


    @BeforeEach
    void setup() {

        property = new Property();
        property.setId(1L);


        propertyDTO = new PropertyDTO();
        propertyDTO.setId(1L);
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
}
