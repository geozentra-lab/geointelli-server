package com.geointelli.ai.property.service.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.CrmLeadCreateRequest;
import com.geointelli.ai.property.service.dto.CrmLeadDTO;
import com.geointelli.ai.property.service.dto.CrmLeadSubmissionDTO;
import com.geointelli.ai.property.service.dto.CrmLeadUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmClient;
import com.geointelli.ai.property.service.entity.CrmLead;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.entity.LeadStage;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.CrmMapper;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmLeadService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import lombok.RequiredArgsConstructor;

/** Serializes lead creation on its client, and stage changes on the affected lead. */
@Service
@Transactional
@RequiredArgsConstructor
public class CrmLeadServiceImpl implements CrmLeadService {
    private final CrmClientRepository clients;
    private final CrmLeadRepository leads;
    private final PropertyRepository properties;
    private final CurrentUserService currentUser;
    private final CrmValidationService validation;
    private final CrmActivityService activity;
    private final CrmMapper mapper;
    private final ObjectMapper json;

    @Override
    public CrmLeadSubmissionDTO create(CrmLeadCreateRequest request, String idempotencyKey) {
        User user = currentUser.getCurrentUserEntity();
        CrmLeadCreateRequest value = normalized(request);
        validation.validate(value);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) {
            throw new IllegalArgumentException("Idempotency-Key is required: 1 to 100 letters, digits, dots, underscores, colons or hyphens");
        }
        String hash = fingerprint(value);
        var existing = leads.findByUser_IdAndRequestKey(user.getId(), idempotencyKey);
        if (existing.isPresent()) return replay(existing.get(), hash);

        CrmClient client = clients.findOwnedForUpdate(value.clientId(), user.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
        // A simultaneous identical request may have committed while this request waited for the client lock.
        existing = leads.findByUser_IdAndRequestKey(user.getId(), idempotencyKey);
        if (existing.isPresent()) return replay(existing.get(), hash);
        checkProperty(value.propertyId());
        CrmLead lead = new CrmLead();
        lead.setUser(user);
        lead.setClient(client);
        lead.setTitle(value.title());
        lead.setSource(value.source());
        lead.setPropertyId(value.propertyId());
        lead.setNotes(value.notes());
        lead.setRequestKey(idempotencyKey);
        lead.setRequestHash(hash);
        leads.saveAndFlush(lead);
        activity.record(user.getId(), client.getId(), CrmSubjectType.LEAD, lead.getId(),
            CrmActivityType.LEAD_CREATED, "Lead created in stage NEW");
        return new CrmLeadSubmissionDTO(mapper.toDTO(lead), true);
    }

    @Override
    public CrmPageDTO<CrmLeadDTO> list(int page, int size, LeadStage stage) {
        Long owner = currentUser.getCurrentUserEntity().getId();
        var pageable = validation.page(page, size);
        var result = stage == null ? leads.findByUser_Id(owner, pageable)
            : leads.findByUser_IdAndStage(owner, stage, pageable);
        return new CrmPageDTO<>(result.map(mapper::toDTO).getContent(), page, size,
            result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public CrmLeadDTO get(Long id) { return mapper.toDTO(owned(id, false)); }

    @Override
    public CrmLeadDTO update(Long id, CrmLeadUpdateRequest request) {
        CrmLead lead = owned(id, true);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        CrmLeadCreateRequest merged = normalized(new CrmLeadCreateRequest(lead.getClient().getId(),
            request.isPropertyIdProvided() ? request.getPropertyId() : lead.getPropertyId(),
            request.isTitleProvided() ? request.getTitle() : lead.getTitle(),
            request.isSourceProvided() ? request.getSource() : lead.getSource(),
            request.isNotesProvided() ? request.getNotes() : lead.getNotes()));
        validation.validate(merged);
        LeadStage next = request.isStageProvided() ? request.getStage() : lead.getStage();
        if (next == null) throw new IllegalArgumentException("stage cannot be null");
        if (!lead.getStage().canChangeTo(next)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Unsupported lead transition: " + lead.getStage() + " -> " + next);
        }
        if (request.isPropertyIdProvided()) checkProperty(merged.propertyId());
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(lead.getTitle(), merged.title())) changed.add("title");
        if (!Objects.equals(lead.getSource(), merged.source())) changed.add("source");
        if (!Objects.equals(lead.getNotes(), merged.notes())) changed.add("notes");
        if (!Objects.equals(lead.getPropertyId(), merged.propertyId())) changed.add("propertyId");
        boolean stageChanged = lead.getStage() != next;
        String transition = "Stage " + lead.getStage() + " -> " + next;
        if (stageChanged) changed.add("stage");
        if (!changed.isEmpty()) {
            lead.setTitle(merged.title());
            lead.setSource(merged.source());
            lead.setNotes(merged.notes());
            lead.setPropertyId(merged.propertyId());
            lead.setStage(next);
            leads.saveAndFlush(lead);
            activity.record(lead.getUser().getId(), lead.getClient().getId(), CrmSubjectType.LEAD, id,
                stageChanged ? CrmActivityType.LEAD_STAGE_CHANGED : CrmActivityType.LEAD_UPDATED,
                (stageChanged ? transition + "; " : "") + "Changed: " + String.join(", ", changed));
        }
        return mapper.toDTO(lead);
    }

    private CrmLead owned(Long id, boolean lock) {
        validation.requirePositive(id, "leadId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        return (lock ? leads.findOwnedForUpdate(id, owner) : leads.findByIdAndUser_Id(id, owner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lead not found"));
    }

    private CrmLeadCreateRequest normalized(CrmLeadCreateRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        return new CrmLeadCreateRequest(request.clientId(), request.propertyId(),
            validation.normalize(request.title()), validation.normalize(request.source()), validation.normalize(request.notes()));
    }

    private void checkProperty(Long id) {
        if (id != null && !properties.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
        }
    }

    private CrmLeadSubmissionDTO replay(CrmLead lead, String hash) {
        if (!lead.getRequestHash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used with different input");
        }
        return new CrmLeadSubmissionDTO(mapper.toDTO(lead), false);
    }

    /** Hashes normalized immutable submission inputs, never the lead's subsequently edited state. */
    private String fingerprint(CrmLeadCreateRequest value) {
        try {
            String canonical = "crm-lead-v1:" + json.writeValueAsString(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException error) {
            throw new IllegalStateException("Could not fingerprint lead submission", error);
        }
    }
}
