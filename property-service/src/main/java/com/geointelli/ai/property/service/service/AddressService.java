package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.entity.Property;

public interface AddressService {
    public List<Long> getAllPropertiesId();
    public List<Property> search(AddressSearchRequest req);
    public List<String> suggest(String raw);
}
