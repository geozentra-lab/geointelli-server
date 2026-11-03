package com.geointelli.ai.property.service.service.impl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmActivityDTO;
import com.geointelli.ai.property.service.dto.CrmNoteRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.CrmActivity;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.mapper.CrmMapper;
import com.geointelli.ai.property.service.repository.CrmActivityRepository;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import lombok.RequiredArgsConstructor;

/** Writes append-only history and applies ownership to both notes and history reads. */
@Service
@Transactional
@RequiredArgsConstructor
public class CrmActivityServiceImpl implements CrmActivityService {
    private final CrmActivityRepository activities;
    private final CrmClientRepository clients;
    private final CrmLeadRepository leads;
    private final CurrentUserService currentUser;
    private final CrmValidationService validation;
    private final CrmMapper mapper;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long ownerId, Long clientId, CrmSubjectType subjectType, Long subjectId,
            CrmActivityType type, String description) {
        activities.save(event(ownerId, clientId, subjectType, subjectId, type, description));
    }

    @Override
    public CrmPageDTO<CrmActivityDTO> list(int page, int size) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        return toPage(activities.findByUserId(owner, validation.page(page, size)));
    }

    @Override
    public CrmPageDTO<CrmActivityDTO> clientHistory(Long id, int page, int size) {
        validation.requirePositive(id, "clientId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        clients.findByIdAndUser_Id(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        return toPage(activities.findByUserIdAndClientId(owner, id, validation.page(page, size)));
    }

    @Override
    public CrmPageDTO<CrmActivityDTO> leadHistory(Long id, int page, int size) {
        validation.requirePositive(id, "leadId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        leads.findByIdAndUser_Id(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        return toPage(activities.findByUserIdAndSubjectTypeAndSubjectId(owner, CrmSubjectType.LEAD, id,
            validation.page(page, size)));
    }

    @Override
    public CrmActivityDTO addClientNote(Long id, CrmNoteRequest request) {
        validation.requirePositive(id, "clientId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        clients.findOwnedForUpdate(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        return saveNote(owner, id, CrmSubjectType.CLIENT, id, request);
    }

    @Override
    public CrmActivityDTO addLeadNote(Long id, CrmNoteRequest request) {
        validation.requirePositive(id, "leadId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        var lead = leads.findOwnedForUpdate(id, owner)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
        return saveNote(owner, lead.getClient().getId(), CrmSubjectType.LEAD, id, request);
    }

    private CrmActivityDTO saveNote(Long owner, Long clientId, CrmSubjectType subjectType, Long subjectId,
            CrmNoteRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        CrmNoteRequest normalized = new CrmNoteRequest(validation.normalize(request.description()));
        validation.validate(normalized);
        return mapper.toDTO(activities.saveAndFlush(event(owner, clientId, subjectType, subjectId,
            CrmActivityType.NOTE_ADDED, normalized.description())));
    }

    private CrmActivity event(Long owner, Long clientId, CrmSubjectType subjectType, Long subjectId,
            CrmActivityType type, String description) {
        CrmActivity activity = new CrmActivity();
        activity.setUserId(owner);
        activity.setClientId(clientId);
        activity.setSubjectType(subjectType);
        activity.setSubjectId(subjectId);
        activity.setActivityType(type);
        activity.setDescription(description);
        activity.setCreatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        return activity;
    }

    private CrmPageDTO<CrmActivityDTO> toPage(Page<CrmActivity> page) {
        return new CrmPageDTO<>(page.map(mapper::toDTO).getContent(), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages());
    }
}
