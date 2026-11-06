package com.geointelli.ai.property.service.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.ContactProfessionalDTO;
import com.geointelli.ai.property.service.dto.ContactResponseDTO;
import com.geointelli.ai.property.service.dto.ContactRequestEventDTO;
import com.geointelli.ai.property.service.dto.ContactRequestSubmissionDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.PropertyContactRequest;
import com.geointelli.ai.property.service.entity.ContactRequestOutbox;
import com.geointelli.ai.property.service.entity.PropertyContact;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.entity.UserPersona;
import com.geointelli.ai.property.service.repository.ContactRequestOutboxRepository;
import com.geointelli.ai.property.service.repository.PropertyContactRepository;
import com.geointelli.ai.property.service.repository.PropertyRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.ContactRequestService;
import com.geointelli.ai.property.service.service.CrmInboundContactService;
import com.geointelli.ai.property.service.service.CrmValidationService;
import lombok.RequiredArgsConstructor;

/** Saves consent, request, recipient-owned CRM lead and immutable event together, without contacting RabbitMQ. */
@Service
@Transactional
@RequiredArgsConstructor
public class ContactRequestServiceImpl implements ContactRequestService {
    public static final String CONSENT_VERSION = "property-contact-v1";
    private final PropertyContactRepository requests;
    private final ContactRequestOutboxRepository outbox;
    private final UserRepository users;
    private final PropertyRepository properties;
    private final CrmValidationService validation;
    private final CrmInboundContactService crm;
    private final ObjectMapper json;

    @Override
    public ContactRequestSubmissionDTO submit(Long requester, PropertyContactRequest request, String idempotencyKey) {
        PropertyContactRequest value = normalized(request);
        validation.validate(value);
        if (!Boolean.TRUE.equals(value.consent()) || !CONSENT_VERSION.equals(value.consentVersion()))
            throw new IllegalArgumentException("Explicit consent=true and consentVersion=" + CONSENT_VERSION + " are required");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
            throw new IllegalArgumentException("Idempotency-Key is required: 1-100 letters, digits, dots, underscores, colons or hyphens");
        String hash = hash(value);
        PropertyContact existing = requests.findByRequesterUserIdAndRequestKey(requester, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, hash);
        // Acquire both user locks in a fixed order, including when the requester is also a professional.
        Long first = Math.min(requester, value.professionalUserId());
        Long second = Math.max(requester, value.professionalUserId());
        User firstUser = users.lockForContact(first).orElseThrow(this::professionalNotFound);
        User secondUser = first.equals(second) ? firstUser : users.lockForContact(second).orElseThrow(this::professionalNotFound);
        User professional = value.professionalUserId().equals(first) ? firstUser : secondUser;
        existing = requests.findByRequesterUserIdAndRequestKey(requester, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, hash);
        if (professional.getPersona() != UserPersona.REALTOR || professional.getKeycloakUserId() == null
                || professional.getKeycloakUserId().isBlank()) throw professionalNotFound();
        if (!properties.existsById(value.propertyId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found");
        Instant submittedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        PropertyContact contact = new PropertyContact();
        contact.setRequesterUserId(requester);
        contact.setProfessionalUserId(professional.getId());
        contact.setPropertyId(value.propertyId());
        contact.setContactName(value.contactName());
        contact.setContactEmail(value.contactEmail());
        contact.setContactPhone(value.contactPhone());
        contact.setMessage(value.message());
        contact.setConsent(true);
        contact.setConsentVersion(CONSENT_VERSION);
        contact.setSubmittedAt(submittedAt);
        contact.setRequestKey(idempotencyKey);
        contact.setRequestHash(hash);
        requests.saveAndFlush(contact);
        contact.setLeadId(crm.createLead(professional, contact));
        requests.saveAndFlush(contact);
        String eventId = "property-contact-" + contact.getId();
        var payload = new ContactRequestEventDTO.ContactPayload(contact.getId(), contact.getLeadId(),
            contact.getPropertyId(), contact.getContactName(), contact.getContactEmail(),
            contact.getContactPhone(), contact.getMessage());
        var event = new ContactRequestEventDTO(eventId, "property-service", 1, "CONTACT_REQUEST",
            professional.getKeycloakUserId(), null, null, contact.getPropertyId(),
            "contact-request:" + contact.getId(), submittedAt, payload);
        ContactRequestOutbox delivery = new ContactRequestOutbox();
        delivery.setRequestId(contact.getId());
        delivery.setEventId(eventId);
        try { delivery.setEventJson(json.writeValueAsString(event)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Could not prepare contact delivery", error); }
        delivery.setNextAttemptAt(submittedAt);
        outbox.saveAndFlush(delivery);
        return new ContactRequestSubmissionDTO(dto(contact), true);
    }

    @Override
    public ContactResponseDTO get(Long actor, Long id) {
        validation.requirePositive(id, "id");
        PropertyContact contact = requests.findById(id).orElseThrow(this::notFound);
        if (!contact.getRequesterUserId().equals(actor) && !contact.getProfessionalUserId().equals(actor)) throw notFound();
        return dto(contact);
    }

    @Override
    public CrmPageDTO<ContactProfessionalDTO> professionals(int page, int size) {
        var result = users.findByPersona(UserPersona.REALTOR, validation.page(page, size));
        var content = result.map(user -> {
            String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
            if (name.isBlank()) name = "Professional " + user.getId();
            return new ContactProfessionalDTO(user.getId(), name);
        });
        return new CrmPageDTO<>(content.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }

    private ContactRequestSubmissionDTO replay(PropertyContact contact, String hash) {
        if (!contact.getRequestHash().equals(hash))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used with different input");
        return new ContactRequestSubmissionDTO(dto(contact), false);
    }

    private ContactResponseDTO dto(PropertyContact contact) {
        var delivery = outbox.findByRequestId(contact.getId()).orElseThrow();
        return new ContactResponseDTO(contact.getId(), contact.getPropertyId(), contact.getProfessionalUserId(),
            contact.getLeadId(), contact.getStatus(), delivery.getStatus(), contact.getSubmittedAt(),
            contact.getContactName(), contact.getContactEmail(), contact.getContactPhone(),
            contact.getMessage(), contact.getConsentVersion());
    }

    private PropertyContactRequest normalized(PropertyContactRequest request) {
        if (request == null) throw new IllegalArgumentException("Request body is required");
        String email = validation.normalize(request.contactEmail());
        return new PropertyContactRequest(request.propertyId(), request.professionalUserId(),
            validation.normalize(request.contactName()), email == null ? null : email.toLowerCase(Locale.ROOT),
            validation.normalize(request.contactPhone()), validation.normalize(request.message()), request.consent(),
            validation.normalize(request.consentVersion()));
    }

    private String hash(PropertyContactRequest value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                ("property-contact-v1:" + json.writeValueAsString(value)).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException error) {
            throw new IllegalStateException("Could not fingerprint contact request", error);
        }
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Contact request not found");
    }
    private ResponseStatusException professionalNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Professional not found");
    }
}

