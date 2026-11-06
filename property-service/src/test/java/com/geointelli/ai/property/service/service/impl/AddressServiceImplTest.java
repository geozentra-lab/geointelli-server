package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.AddressRepository;
import com.geointelli.ai.property.service.service.PropertySearchService;

class AddressServiceImplTest {
    private final AddressRepository addresses = mock(AddressRepository.class);
    private final PropertySearchService search = mock(PropertySearchService.class);
    private final AddressServiceImpl service = new AddressServiceImpl(addresses, search);

    @Test
    void rawPatternMatchesTheIndexedExpressionAndTreatsWildcardsLiterally() {
        assertEquals("%13660 sw 79 st unincorporated%",
            AddressServiceImpl.rawAddressPattern("13660 SW 79 ST, Unincorporated"));
        assertEquals("%nw 5th st%", AddressServiceImpl.rawAddressPattern("N.W. 5th: St"));
        assertEquals("%50\\% off\\_x\\\\%", AddressServiceImpl.rawAddressPattern("50% off_x\\"));
    }

    @Test
    void rawSearchReturnsAtMostTwentyCardsInRankedOrder() {
        PropertySearchItem first = card(9L);
        PropertySearchItem second = card(4L);
        when(addresses.findPropertyIdsByRawAddress("brickell ave", "%brickell ave%", 1000, 20))
            .thenReturn(List.of(9L, 4L));
        when(search.findCards(List.of(9L, 4L))).thenReturn(List.of(first, second));

        AddressSearchRequest request = AddressSearchRequest.builder().rawAddress(" brickell ave ").build();

        assertEquals(List.of(first, second), service.search(request));
    }

    @Test
    void fieldSearchLowerCasesTextFiltersAndCapsResults() {
        when(addresses.findPropertyIdsByAddressFields(100, "collins", "miami beach", "33139", null, "fl", 1000, 20))
            .thenReturn(List.of(1L));
        when(search.findCards(List.of(1L))).thenReturn(List.of(card(1L)));

        AddressSearchRequest request = AddressSearchRequest.builder()
            .streetNumber(100).streetName("Collins").city("Miami Beach").zip("33139").state("FL").build();

        assertEquals(1, service.search(request).size());
    }

    @Test
    void noMatchIsNotFound() {
        when(addresses.findPropertyIdsByRawAddress(anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(search.findCards(List.of())).thenReturn(List.of());

        assertThrows(PropertyNotFoundException.class,
            () -> service.search(AddressSearchRequest.builder().rawAddress("nowhere").build()));
    }

    private static PropertySearchItem card(Long id) {
        return new PropertySearchItem(id, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    }
}
