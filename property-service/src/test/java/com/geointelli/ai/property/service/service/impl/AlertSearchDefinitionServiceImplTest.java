package com.geointelli.ai.property.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.entity.*;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;

class AlertSearchDefinitionServiceImplTest {
    final ObjectMapper json=new ObjectMapper();
    final AlertSearchDefinitionServiceImpl service=new AlertSearchDefinitionServiceImpl(json,mock(PropertySearchValidationService.class));
    final AlertRule rule=new AlertRule();
    final SavedSearch saved=new SavedSearch();
    AlertSearchDefinitionServiceImplTest() {
        User user=new User(); user.setId(1L); rule.setUser(user); saved.setUser(user); saved.setId(4L);
        saved.setFiltersVersion(1); rule.setSavedSearch(saved);
    }
    @Test void sortDoesNotChangeMembershipFingerprint() throws Exception {
        saved.setFilters(json.readTree("{\"city\":\"Miami\",\"sortBy\":\"id\"}"));
        String hash=service.resolve(rule).hash();
        saved.setFilters(json.readTree("{\"sortBy\":\"bedrooms\",\"city\":\"Miami\"}"));
        assertEquals(hash,service.resolve(rule).hash());
        saved.setFilters(json.readTree("{\"city\":\"Orlando\"}"));
        assertNotEquals(hash,service.resolve(rule).hash());
    }
    @Test void unsupportedVersionIsRejected() {
        saved.setFiltersVersion(2); assertThrows(IllegalArgumentException.class,()->service.resolve(rule));
    }
    @Test void differentOwnerIsRejected() {
        User other=new User(); other.setId(2L); saved.setUser(other);
        assertThrows(IllegalArgumentException.class,()->service.resolve(rule));
    }
}
