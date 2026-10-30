package com.geointelli.ai.property.service.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.dto.AlertSearchDefinitionDTO;
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.service.AlertSearchDefinitionService;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;

/** Does not execute a search or assume a request-thread bearer token exists in the background. */
@Service
public class AlertSearchDefinitionServiceImpl implements AlertSearchDefinitionService {
    private final ObjectMapper json;
    private final PropertySearchValidationService validation;

    public AlertSearchDefinitionServiceImpl(ObjectMapper json, PropertySearchValidationService validation) {
        this.json = json; this.validation = validation;
    }

    @Override
    public AlertSearchDefinitionDTO resolve(AlertRule rule) {
        SavedSearch search = rule.getSavedSearch();
        if (search == null || !search.getUser().getId().equals(rule.getUser().getId())) {
            throw new IllegalArgumentException("INVALID_SEARCH_OWNER");
        }
        if (search.getFiltersVersion() != 1) throw new IllegalArgumentException("UNSUPPORTED_FILTER_VERSION");
        try {
            SavedSearchFilters filters = json.treeToValue(search.getFilters(), SavedSearchFilters.class);
            validation.validate(filters);
            ObjectNode node = json.valueToTree(filters);
            node.remove(List.of("page", "size", "sortBy", "direction"));
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(field -> {
                if (!field.getValue().isNull()) sorted.put(field.getKey(), field.getValue());
            });
            String identity = search.getId() + ":1:" + json.writeValueAsString(sorted);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8)));
            String stamp = rule.getUpdatedAt() + ":" + search.getUpdatedAt();
            return new AlertSearchDefinitionDTO(filters, hash, stamp);
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("INVALID_STORED_FILTERS", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
