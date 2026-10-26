package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.dto.SavedSearchCreate;
import com.geointelli.ai.property.service.dto.SavedSearchDTO;
import com.geointelli.ai.property.service.dto.SavedSearchFilters;
import com.geointelli.ai.property.service.dto.SavedSearchPageDTO;
import com.geointelli.ai.property.service.dto.SavedSearchRequest;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;
import com.geointelli.ai.property.service.service.SavedSearchService;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

/** Persists canonical filter payloads and delegates result queries to the existing property search service. */
@Service
@Transactional
public class SavedSearchServiceImpl implements SavedSearchService {
    private static final int FILTERS_VERSION = 1;
    private final SavedSearchRepository searches;
    private final CurrentUserService currentUser;
    private final PropertySearchService propertySearch;
    private final PropertySearchValidationService searchValidation;
    private final ObjectMapper json;
    private final Validator validator;
    private final AlertEvaluationScheduleService evaluationSchedule;

    public SavedSearchServiceImpl(SavedSearchRepository searches, CurrentUserService currentUser,
            PropertySearchService propertySearch, PropertySearchValidationService searchValidation,
            ObjectMapper json, Validator validator,
            AlertEvaluationScheduleService evaluationSchedule) {
        this.searches = searches;
        this.currentUser = currentUser;
        this.propertySearch = propertySearch;
        this.searchValidation = searchValidation;
        this.json = json;
        this.validator = validator;
        this.evaluationSchedule = evaluationSchedule;
    }

    @Override
    public SavedSearchDTO create(SavedSearchRequest request) {
        User owner = currentUser.getCurrentUserEntity();
        validate(request, SavedSearchCreate.class);
        JsonNode filters = canonicalFilters(request.getFilters());
        SavedSearch search = new SavedSearch();
        search.setUser(owner);
        search.setName(request.getName().trim());
        search.setFilters(filters);
        if (request.isCadenceProvided()) search.setCadence(request.getCadence());
        if (request.isPausedProvided()) search.setPaused(request.getPaused());
        searches.saveAndFlush(search);
        evaluationSchedule.rescheduleSearch(search.getId());
        return toDTO(search);
    }

    @Override
    public SavedSearchPageDTO list(int page, int size) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        validatePage(page, size);
        Page<SavedSearch> result = searches.findByUser_Id(owner,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<SavedSearchDTO> content = new ArrayList<>();
        for (SavedSearch search : result) content.add(toDTO(search));
        return new SavedSearchPageDTO(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public SavedSearchDTO get(Long id) {
        return toDTO(owned(id, false));
    }

    @Override
    public SavedSearchDTO update(Long id, SavedSearchRequest request) {
        SavedSearch search = owned(id, true);
        validate(request, Default.class);
        JsonNode filters = request.isFiltersProvided() ? canonicalFilters(request.getFilters()) : null;
        if (request.isNameProvided()) search.setName(request.getName().trim());
        if (request.isFiltersProvided()) {
            search.setFilters(filters);
            search.setFiltersVersion(FILTERS_VERSION);
        }
        if (request.isCadenceProvided()) search.setCadence(request.getCadence());
        if (request.isPausedProvided()) search.setPaused(request.getPaused());
        searches.saveAndFlush(search);
        evaluationSchedule.rescheduleSearch(search.getId());
        return toDTO(search);
    }

    @Override
    public void delete(Long id) {
        searches.delete(owned(id, true));
    }

    @Override
    public PropertySearchResponse results(Long id, int page, int size) {
        SavedSearch search = owned(id, false);
        validatePage(page, size);
        if (search.getFiltersVersion() != FILTERS_VERSION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported saved filter version; replace the filters");
        }
        PropertySearchRequest request;
        try {
            SavedSearchFilters filters = json.treeToValue(search.getFilters(), SavedSearchFilters.class);
            searchValidation.validate(filters);
            request = filters;
        } catch (JsonProcessingException | IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stored filters are invalid; replace the filters");
        }
        request.setPage(page);
        request.setSize(size);
        searchValidation.validate(request);
        return propertySearch.search(request);
    }

    private SavedSearch owned(Long id, boolean lock) {
        if (id == null || id <= 0) throw new IllegalArgumentException("savedSearchId must be positive");
        Long owner = currentUser.getCurrentUserEntity().getId();
        return (lock ? searches.findOwnedForUpdate(id, owner) : searches.findByIdAndUser_Id(id, owner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
    }

    private void validate(SavedSearchRequest request, Class<?> group) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        for (ConstraintViolation<SavedSearchRequest> violation : validator.validate(request, group)) {
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
    }

    /** Mirrors text matching in property search and removes null/blank fields and transient pagination. */
    private JsonNode canonicalFilters(SavedSearchFilters filters) {
        searchValidation.validate(filters);
        ObjectNode result = json.valueToTree(filters);
        result.remove(List.of("page", "size"));
        List<String> nullFields = new ArrayList<>();
        result.fields().forEachRemaining(field -> {
            if (field.getValue().isNull()) nullFields.add(field.getKey());
        });
        result.remove(nullFields);
        for (String name : List.of("state", "city", "zip", "address", "neighborhood", "propertyType", "dorCode", "direction")) {
            if (result.has(name)) {
                String value = result.get(name).asText().trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) result.remove(name);
                else result.put(name, value);
            }
        }
        return result;
    }

    private SavedSearchDTO toDTO(SavedSearch search) {
        return new SavedSearchDTO(search.getId(), search.getName(), search.getFilters().deepCopy(),
            search.getFiltersVersion(), search.getCadence(), search.isPaused(), search.getCreatedAt(), search.getUpdatedAt());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page must be non-negative, size must be 1-100, and offset must fit an integer");
        }
    }
}
