package com.geointelli.ai.property.service.service.impl;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.geointelli.ai.property.service.dto.AddressSearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.exception.PropertyNotFoundException;
import com.geointelli.ai.property.service.repository.AddressRepository;
import com.geointelli.ai.property.service.service.AddressService;
import com.geointelli.ai.property.service.service.PropertySearchService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {
    /** The search screen shows only the first results. */
    static final int RESULT_LIMIT = 20;
    /** Matches examined before ranking; broad terms match a large share of all addresses. */
    static final int CANDIDATE_LIMIT = 1000;

    private final AddressRepository addressRepository;
    private final PropertySearchService propertySearchService;

    @Override
    public List<Long> getAllPropertiesId(){
        return addressRepository.findAllPropertiesId();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertySearchItem> search(AddressSearchRequest req) {
        List<Long> ids;
        String notFound;
        if (StringUtils.hasText(req.getRawAddress())) {
            String raw = req.getRawAddress().trim();
            ids = addressRepository.findPropertyIdsByRawAddress(
                raw, rawAddressPattern(raw), CANDIDATE_LIMIT, RESULT_LIMIT);
            notFound = "No property found matching: " + req.getRawAddress();
        } else {
            ids = addressRepository.findPropertyIdsByAddressFields(
                req.getStreetNumber(), lower(req.getStreetName()), lower(req.getCity()),
                lower(req.getZip()), lower(req.getUnit()), lower(req.getState()),
                CANDIDATE_LIMIT, RESULT_LIMIT);
            notFound = "No property found for the given address fields";
        }

        List<PropertySearchItem> results = propertySearchService.findCards(ids);
        if (results.isEmpty()) {
            throw new PropertyNotFoundException(notFound);
        }
        return results;
    }

    @Override
    public List<String> suggest(String raw) {
        if (raw == null || raw.trim().length() < 3) return List.of();
        return addressRepository.findAddressSuggestions(raw.trim());
    }

    /**
     * Substring pattern for the address with commas, dots and colons removed and lower-cased, matching
     * the indexed expression. Wildcards typed by the user are matched literally.
     */
    static String rawAddressPattern(String raw) {
        String stripped = raw.replace(",", "").replace(".", "").replace(":", "").toLowerCase(Locale.ROOT);
        String literal = stripped.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + literal + "%";
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
