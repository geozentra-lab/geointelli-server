package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.geointelli.ai.property.service.dto.PropertyDTO;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.mapper.PropertyMapper;
import com.geointelli.ai.property.service.repository.PropertyRepository;

@ExtendWith(MockitoExtension.class)
class PropertyCountyTest {
    @Mock PropertyRepository repository;
    @Mock PropertyMapper mapper;
    @InjectMocks PropertyServiceImpl service;

    @Test
    void sameFolioResolvesDifferentPropertiesByCounty() {
        Property first = property(1L, 10L);
        Property second = property(2L, 20L);
        PropertyDTO firstDto = new PropertyDTO();
        PropertyDTO secondDto = new PropertyDTO();
        when(repository.findByFolioAndCounty_Id("00123", 10L)).thenReturn(Optional.of(first));
        when(repository.findByFolioAndCounty_Id("00123", 20L)).thenReturn(Optional.of(second));
        when(mapper.toDTO(same(first))).thenReturn(firstDto);
        when(mapper.toDTO(same(second))).thenReturn(secondDto);
        assertSame(firstDto, service.getByFolio("00123", 10L));
        assertSame(secondDto, service.getByFolio("00123", 20L));
    }

    @Test
    void saveOnlyReusesIdentityWithinTheSameCounty() {
        Property incoming = property(null, 20L);
        when(repository.findByFolioAndCounty_Id("00123", 20L)).thenReturn(Optional.empty());
        when(repository.save(incoming)).thenReturn(incoming);
        service.saveProperty(incoming);
        assertNull(incoming.getId());
        verify(repository, never()).findByFolioAndCounty_Id("00123", 10L);

        when(repository.findByFolioAndCounty_Id("00123", 20L)).thenReturn(Optional.of(property(2L, 20L)));
        service.saveProperty(incoming);
        assertEquals(2L, incoming.getId());
    }

    @Test
    void rejectsUnscopedLookupAndSave() {
        assertThrows(IllegalArgumentException.class, () -> service.getByFolio("00123", null));
        assertThrows(IllegalArgumentException.class, () -> service.saveProperty(new Property()));
        verifyNoInteractions(repository);
    }

    private Property property(Long id, Long countyId) {
        County county = new County();
        county.setId(countyId);
        Property property = new Property();
        property.setId(id);
        property.setFolio("00123");
        property.setCounty(county);
        return property;
    }
}
