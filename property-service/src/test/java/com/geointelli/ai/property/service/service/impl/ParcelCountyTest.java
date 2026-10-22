package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.mapper.ParcelMapper;
import com.geointelli.ai.property.service.repository.ParcelRepository;

class ParcelCountyTest {
    private final ParcelRepository repository = mock(ParcelRepository.class);
    private final ParcelServiceImpl service = new ParcelServiceImpl(repository, mock(ParcelMapper.class));

    @Test
    void parcelLookupRequiresAndUsesCounty() {
        service.getByFolio("00123", 20L);
        verify(repository).findByFolioAndProperty_County_Id("00123", 20L);
        assertThrows(IllegalArgumentException.class, () -> service.getByFolio("00123", null));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void preloadRetainsRowsWithRepeatedFolios() {
        Parcel first = new Parcel();
        first.setId(1L);
        first.setFolio("00123");
        Parcel second = new Parcel();
        second.setId(2L);
        second.setFolio("00123");
        when(repository.findAll()).thenReturn(List.of(first, second));
        var result = service.preloadParcels();
        assertSame(first, result.get(1L));
        assertSame(second, result.get(2L));
    }
}
