package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.geointelli.ai.property.service.dto.CrmClientCreateRequest;
import com.geointelli.ai.property.service.dto.CrmClientDTO;
import com.geointelli.ai.property.service.dto.CrmClientUpdateRequest;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.entity.CrmActivityType;
import com.geointelli.ai.property.service.entity.CrmClient;
import com.geointelli.ai.property.service.entity.CrmSubjectType;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.mapper.CrmMapper;
import com.geointelli.ai.property.service.repository.CrmClientRepository;
import com.geointelli.ai.property.service.repository.CrmLeadRepository;
import com.geointelli.ai.property.service.repository.CrmTaskRepository;
import com.geointelli.ai.property.service.repository.CrmTransactionRepository;
import com.geointelli.ai.property.service.service.CrmActivityService;
import com.geointelli.ai.property.service.service.CrmClientService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import lombok.RequiredArgsConstructor;

/** Stores owned contacts and their real changes in the same transaction. */
@Service
@Transactional
@RequiredArgsConstructor
public class CrmClientServiceImpl implements CrmClientService {
    private final CrmClientRepository clients;
    private final CrmLeadRepository leads;
    private final CrmTaskRepository tasks;
    private final CrmTransactionRepository transactions;
    private final CurrentUserService currentUser;
    private final CrmValidationService validation;
    private final CrmActivityService activity;
    private final CrmMapper mapper;

    @Override
    public CrmClientDTO create(CrmClientCreateRequest request) {
        User user = currentUser.getCurrentUserEntity();
        CrmClientCreateRequest value = normalized(request);
        checkContact(value);
        checkDuplicate(user.getId(), value.email(), null);
        CrmClient client = new CrmClient();
        client.setUser(user);
        apply(client, value);
        clients.saveAndFlush(client);
        activity.record(user.getId(), client.getId(), CrmSubjectType.CLIENT, client.getId(),
            CrmActivityType.CLIENT_CREATED, "Client created");
        return mapper.toDTO(client);
    }

    @Override
    public CrmPageDTO<CrmClientDTO> list(int page, int size) {
        Long ownerId = currentUser.getCurrentUserEntity().getId();
        var result = clients.findByUser_Id(ownerId, validation.page(page, size));
        return new CrmPageDTO<>(result.map(mapper::toDTO).getContent(), page, size,
            result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public CrmClientDTO get(Long id) {
        return mapper.toDTO(owned(id, false));
    }

    @Override
    public CrmClientDTO update(Long id, CrmClientUpdateRequest request) {
        CrmClient client = owned(id, true);
        if (request == null) throw new IllegalArgumentException("Request body is required");
        CrmClientCreateRequest merged = normalized(new CrmClientCreateRequest(
            request.isNameProvided() ? request.getName() : client.getName(),
            request.isEmailProvided() ? request.getEmail() : client.getEmail(),
            request.isPhoneProvided() ? request.getPhone() : client.getPhone(),
            request.isNotesProvided() ? request.getNotes() : client.getNotes()));
        checkContact(merged);
        checkDuplicate(client.getUser().getId(), merged.email(), id);
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(client.getName(), merged.name())) changed.add("name");
        if (!Objects.equals(client.getEmail(), merged.email())) changed.add("email");
        if (!Objects.equals(client.getPhone(), merged.phone())) changed.add("phone");
        if (!Objects.equals(client.getNotes(), merged.notes())) changed.add("notes");
        if (!changed.isEmpty()) {
            apply(client, merged);
            clients.saveAndFlush(client);
            activity.record(client.getUser().getId(), id, CrmSubjectType.CLIENT, id,
                CrmActivityType.CLIENT_UPDATED, "Changed: " + String.join(", ", changed));
        }
        return mapper.toDTO(client);
    }

    @Override
    public void delete(Long id) {
        CrmClient client = owned(id, true);
        if (leads.existsByClient_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client has leads and cannot be deleted");
        }
        if (tasks.existsByClientId(id) || transactions.existsByClientId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Client has tasks or transactions and cannot be deleted");
        }
        activity.record(client.getUser().getId(), id, CrmSubjectType.CLIENT, id,
            CrmActivityType.CLIENT_DELETED, "Client deleted");
        clients.delete(client);
        clients.flush();
    }

    private CrmClient owned(Long id, boolean lock) {
        validation.requirePositive(id, "clientId");
        Long owner = currentUser.getCurrentUserEntity().getId();
        return (lock ? clients.findOwnedForUpdate(id, owner) : clients.findByIdAndUser_Id(id, owner))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found"));
    }

    private CrmClientCreateRequest normalized(CrmClientCreateRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        String email = validation.normalize(request.email());
        return new CrmClientCreateRequest(validation.normalize(request.name()),
            email == null ? null : email.toLowerCase(Locale.ROOT), validation.normalize(request.phone()),
            validation.normalize(request.notes()));
    }

    private void checkContact(CrmClientCreateRequest value) {
        validation.validate(value);
        if (value.email() == null && value.phone() == null) {
            throw new IllegalArgumentException("At least one email or phone is required");
        }
        if (value.phone() != null) {
            long digits = value.phone().chars().filter(Character::isDigit).count();
            if (digits < 7 || digits > 15) throw new IllegalArgumentException("phone must contain 7 to 15 digits");
        }
    }

    private void checkDuplicate(Long owner, String email, Long excludingId) {
        if (email != null) {
            clients.findByUser_IdAndEmail(owner, email).ifPresent(existing -> {
                if (!Objects.equals(existing.getId(), excludingId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "A client with this email already exists");
                }
            });
        }
    }

    private void apply(CrmClient client, CrmClientCreateRequest value) {
        client.setName(value.name());
        client.setEmail(value.email());
        client.setPhone(value.phone());
        client.setNotes(value.notes());
    }
}
