package com.geointelli.ai.property.service.service;

import java.util.List;

import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchItem;

public interface AddressService {
    public List<Long> getAllPropertiesId();
    /** Returns up to the first 20 matching property cards; throws PropertyNotFoundException when none match. */
    public List<PropertySearchItem> search(AddressSearchRequest req);
    public List<String> suggest(String raw);
}
