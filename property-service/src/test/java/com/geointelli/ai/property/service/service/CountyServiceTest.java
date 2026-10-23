package com.geointelli.ai.property.service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.service.impl.CountyServiceImpl;

class CountyServiceTest {
    private final CountyRepository repository = mock(CountyRepository.class);
    private final CountyService service = new CountyServiceImpl(repository);

    @Test
    void resolvesCountyWithStateAndTrimsInput() {
        County floridaCounty = new County();
        County georgiaCounty = new County();
        when(repository.findByCountyAndState("Orange", "FL")).thenReturn(Optional.of(floridaCounty));
        when(repository.findByCountyAndState("Orange", "GA")).thenReturn(Optional.of(georgiaCounty));
        assertSame(floridaCounty, service.resolve(" Orange ", " FL "));
        assertSame(georgiaCounty, service.resolve("Orange", "GA"));
    }

    @Test
    void rejectsUnknownOrMissingGeographyRatherThanGuessing() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve("Orange", "XX"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(" ", "FL"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve("Orange", null));
        verify(repository, never()).save(any());
    }
}
