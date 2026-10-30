package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.AlertRuleCreate;
import com.geointelli.ai.property.service.dto.AlertRuleDTO;
import com.geointelli.ai.property.service.dto.AlertRulePageDTO;
import com.geointelli.ai.property.service.dto.AlertRuleRequest;
import com.geointelli.ai.property.service.entity.AlertCadence;
import com.geointelli.ai.property.service.entity.AlertEventType;
import com.geointelli.ai.property.service.entity.AlertRule;
import com.geointelli.ai.property.service.entity.SavedSearch;
import com.geointelli.ai.property.service.entity.SavedSearchCadence;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.AlertRuleMapper;
import com.geointelli.ai.property.service.repository.AlertRuleRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.SavedSearchRepository;
import com.geointelli.ai.property.service.service.AlertRuleService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.AlertEvaluationScheduleService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;

/** Validates target identity and ownership before persisting rule configuration. */
@Service
@Transactional
public class AlertRuleServiceImpl implements AlertRuleService {
    private final AlertRuleRepository rules;
    private final SavedSearchRepository searches;
    private final PropertyRepository properties;
    private final CurrentUserService currentUser;
    private final AlertRuleMapper mapper;
    private final Validator validator;
    private final AlertEvaluationScheduleService evaluationSchedule;

    public AlertRuleServiceImpl(AlertRuleRepository rules, SavedSearchRepository searches,
            PropertyRepository properties, CurrentUserService currentUser, AlertRuleMapper mapper, Validator validator,
            AlertEvaluationScheduleService evaluationSchedule) {
        this.rules = rules;
        this.searches = searches;
        this.properties = properties;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.validator = validator;
        this.evaluationSchedule = evaluationSchedule;
    }

    @Override
    public AlertRuleDTO create(AlertRuleRequest request) {
        User owner = currentUser.getCurrentUserEntity();
        validate(request, AlertRuleCreate.class);
        if ((request.getPropertyId() == null) == (request.getSavedSearchId() == null)) {
            throw new IllegalArgumentException("Exactly one of propertyId or savedSearchId is required");
        }
        if (request.getEventType() == AlertEventType.NEW_MATCH && request.getSavedSearchId() == null) {
            throw new IllegalArgumentException("NEW_MATCH requires a saved search");
        }
        AlertRule rule = new AlertRule();
        rule.setUser(owner);
        rule.setName(request.getName().trim());
        rule.setEventType(request.getEventType());
        if (request.getSavedSearchId() != null) {
            // Uses the same target-row lock as saved-search deletion so validation cannot race that deletion.
            SavedSearch search = searches.findOwnedForUpdate(request.getSavedSearchId(), owner.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Saved search not found"));
            if (rules.existsByUser_IdAndSavedSearch_IdAndEventType(owner.getId(), search.getId(), request.getEventType())) {
                throw duplicate();
            }
            rule.setSavedSearch(search);
            rule.setCadence(defaultCadence(search.getCadence()));
        } else {
            if (!properties.existsById(request.getPropertyId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
            }
            if (rules.existsByUser_IdAndProperty_IdAndEventType(owner.getId(), request.getPropertyId(), request.getEventType())) {
                throw duplicate();
            }
            rule.setProperty(properties.getReferenceById(request.getPropertyId()));
        }
        if (request.hasField("cadence")) rule.setCadence(request.getCadence());
        if (request.hasField("paused")) rule.setPaused(request.getPaused());
        rules.saveAndFlush(rule);
        evaluationSchedule.rescheduleRule(rule.getId());
        return mapper.toDTO(rule);
    }

    @Override
    public AlertRulePageDTO list(int page, int size) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        validatePage(page, size);
        Page<AlertRule> result = rules.findByUser_Id(owner,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        List<AlertRuleDTO> content = new ArrayList<>();
        for (AlertRule rule : result) content.add(mapper.toDTO(rule));
        return new AlertRulePageDTO(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public AlertRuleDTO get(Long id) {
        return mapper.toDTO(owned(id, false));
    }

    @Override
    public AlertRuleDTO update(Long id, AlertRuleRequest request) {
        AlertRule rule = owned(id, true);
        validate(request, Default.class);
        Long propertyId = rule.getProperty() == null ? null : rule.getProperty().getId();
        Long searchId = rule.getSavedSearch() == null ? null : rule.getSavedSearch().getId();
        if ((request.hasField("propertyId") && !Objects.equals(propertyId, request.getPropertyId()))
                || (request.hasField("savedSearchId") && !Objects.equals(searchId, request.getSavedSearchId()))
                || (request.hasField("eventType") && rule.getEventType() != request.getEventType())) {
            throw new IllegalArgumentException("Target and eventType cannot be changed; delete the rule and create another");
        }
        if (request.hasField("name")) rule.setName(request.getName().trim());
        if (request.hasField("cadence")) rule.setCadence(request.getCadence());
        if (request.hasField("paused")) rule.setPaused(request.getPaused());
        rules.saveAndFlush(rule);
        evaluationSchedule.rescheduleRule(rule.getId());
        return mapper.toDTO(rule);
    }

    @Override
    public void delete(Long id) {
        rules.delete(owned(id, true));
    }

    private AlertRule owned(Long id, boolean lock) {
        if (id == null || id <= 0) throw new IllegalArgumentException("alertId must be positive");
        Long owner = currentUser.getCurrentUserEntity().getId();
        return (lock ? rules.findOwnedForUpdate(id, owner) : rules.findByIdAndUser_Id(id, owner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert rule not found"));
    }

    private void validate(AlertRuleRequest request, Class<?> group) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        for (ConstraintViolation<AlertRuleRequest> violation : validator.validate(request, group)) {
            throw new IllegalArgumentException(violation.getPropertyPath() + ": " + violation.getMessage());
        }
    }

    private AlertCadence defaultCadence(SavedSearchCadence cadence) {
        if (cadence == SavedSearchCadence.DAILY) return AlertCadence.DAILY;
        if (cadence == SavedSearchCadence.WEEKLY) return AlertCadence.WEEKLY;
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Unsupported saved search cadence");
    }

    private ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "A rule already exists for this target and event type");
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page must be non-negative, size must be 1-100, and offset must fit an integer");
        }
    }
}
