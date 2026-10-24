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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PropertyServiceImplTest {

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private PropertyMapper propertyMapper;

    @InjectMocks
    private PropertyServiceImpl service;

    private Property property;
    private PropertyDTO propertyDTO;
    private Property child;

    @BeforeEach
    void setup() {
        property = new Property();
        property.setId(1L);

        propertyDTO = new PropertyDTO();
        propertyDTO.setId(1L);

        child = new Property();
        child.setId(101L);
        child.setParentProperty(property);
    }

    @Nested
    @DisplayName("getPropertyById")
    class GetPropertyByIdTest {

        @Test
        void whenPropertyExists_thenReturnMappedProperty() {
            when(propertyRepository.findById(1L))
                    .thenReturn(Optional.of(property));
            when(propertyMapper.toDTO(property)).thenReturn(propertyDTO);

            PropertyDTO result = service.getPropertyById(1L);

            assertSame(propertyDTO, result);
            verify(propertyRepository).findById(1L);
            verify(propertyMapper).toDTO(property);
        }

        @Test
        void whenPropertyDoesNotExist_thenThrowPropertyNotFoundException() {
            when(propertyRepository.findById(1L))
                    .thenReturn(Optional.empty());

            PropertyNotFoundException exception = assertThrows(
                    PropertyNotFoundException.class,
                    () -> service.getPropertyById(1L)
            );

            assertEquals(
                    "No property found with id: 1",
                    exception.getMessage()
            );
            verifyNoInteractions(propertyMapper);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {0L, -1L})
        void whenIdIsInvalid_thenRejectWithoutAccessingRepository(Long id) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.getPropertyById(id)
            );

            assertEquals(
                    "id must be a positive number",
                    exception.getMessage()
            );
            verifyNoInteractions(propertyRepository, propertyMapper);
        }
    }

    @Nested
    @DisplayName("getChildPropertiesByParentId")
    class GetChildPropertiesByParentIdTest {

        @Test
        void whenParentHasChildren_thenReturnMappedPage() {
            PageRequest pageable = PageRequest.of(0, 10);
            ChildPropertyDTO childDTO = mock(ChildPropertyDTO.class);

            when(propertyRepository.existsById(1L)).thenReturn(true);
            when(propertyRepository.findChildPropertiesByParentId(
                    1L, pageable
            )).thenReturn(new PageImpl<>(List.of(child), pageable, 1));
            when(propertyMapper.toChildDTO(child)).thenReturn(childDTO);

            PageResponse<ChildPropertyDTO> result =
                    service.getChildPropertiesByParentId(1L, 0, 10);

            assertEquals(List.of(childDTO), result.content());
            assertEquals(0, result.page());
            assertEquals(10, result.size());
            assertEquals(1L, result.totalElements());
            assertEquals(1L, result.totalPages());
            assertTrue(result.first());
            assertTrue(result.last());

            verify(propertyMapper).toChildDTO(child);
        }

        @Test
        void whenParentHasNoChildren_thenReturnEmptyPage() {
            PageRequest pageable = PageRequest.of(0, 10);

            when(propertyRepository.existsById(1L)).thenReturn(true);
            when(propertyRepository.findChildPropertiesByParentId(
                    1L, pageable
            )).thenReturn(Page.empty(pageable));

            PageResponse<ChildPropertyDTO> result =
                    service.getChildPropertiesByParentId(1L, 0, 10);

            assertTrue(result.content().isEmpty());
            assertEquals(0L, result.totalElements());
            assertEquals(0L, result.totalPages());
            verifyNoInteractions(propertyMapper);
        }

        @Test
        void whenParentDoesNotExist_thenThrowPropertyNotFoundException() {
            when(propertyRepository.existsById(1L)).thenReturn(false);

            PropertyNotFoundException exception = assertThrows(
                    PropertyNotFoundException.class,
                    () -> service.getChildPropertiesByParentId(1L, 0, 10)
            );

            assertEquals(
                    "No property found with id: 1",
                    exception.getMessage()
            );
            verify(propertyRepository, never())
                    .findChildPropertiesByParentId(any(), any());
            verifyNoInteractions(propertyMapper);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(longs = {0L, -1L})
        void whenParentIdIsInvalid_thenRejectWithoutAccessingRepository(
                Long parentId
        ) {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> service.getChildPropertiesByParentId(parentId, 0, 10)
            );

            assertEquals(
                    "id must be a positive number",
                    exception.getMessage()
            );
            verifyNoInteractions(propertyRepository, propertyMapper);
        }
    }
}