package com.geointelli.ai.property.service.service.impl;

import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.geointelli.ai.property.service.client.MiamiDadeApiClient;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CountyService;

@ExtendWith(MockitoExtension.class)
class MiamiDadeCountyTest {
    @Mock PropertyRepository repository;
    @Mock CountyService countyService;
    @Mock MiamiDadeApiClient client;
    @InjectMocks PropertyIngestionServiceImpl service;

    @Test
    void refreshOnlyLooksForPropertyInProvidersCounty() {
        County miamiDade = new County();
        miamiDade.setId(10L);
        when(countyService.miamiDade()).thenReturn(miamiDade);
        when(repository.findByFolioAndCounty_Id("00123", 10L)).thenReturn(Optional.empty());
        service.ingestAddresses("00123");
        verify(repository).findByFolioAndCounty_Id("00123", 10L);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(client);
    }
}
