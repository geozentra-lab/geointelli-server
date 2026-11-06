package com.geointelli.ai.property.service.controller;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.ContactProfessionalDTO;
import com.geointelli.ai.property.service.dto.ContactResponseDTO;
import com.geointelli.ai.property.service.dto.CrmPageDTO;
import com.geointelli.ai.property.service.dto.PropertyContactRequest;
import com.geointelli.ai.property.service.service.ContactRequestService;
import com.geointelli.ai.property.service.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Authenticates before transaction entry, so first-login provisioning does not nest inside submission locks. */
@RestController
@RequestMapping("/api/contact-requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Property contact requests", description = "Authenticated requests to a selected REALTOR account. "
    + "No listing-agent association is inferred. Missing token 401; invalid input 400; missing property/professional "
    + "or foreign request 404; changed idempotency input or concurrent reference conflict 409.")
public class ContactRequestController {
    private final ContactRequestService contacts;
    private final CurrentUserService currentUser;

    @PostMapping
    @Operation(summary = "Ask a selected professional to contact me about a property", description = "Requires "
        + "Idempotency-Key (1-100 letters/digits/dots/underscores/colons/hyphens), propertyId, professionalUserId, "
        + "contactName (1-120), a contactEmail (max 254) or contactPhone (6-40), consent=true and "
        + "consentVersion=property-contact-v1. The UI must explicitly ask permission to share these details and "
        + "the optional message (max 2000) with the selected professional about this property. Not marketing consent. "
        + "Email is trimmed/lowercased; optional blank fields become null. Creates a NEW lead owned by the professional. "
        + "An existing professional-owned client with the same email is reused without overwriting their details; "
        + "phone-only submissions do not infer client identity. First submission returns 201, identical requester/key/input "
        + "returns 200 with the same request and lead. Different input with the same key returns 409. "
        + "Request, consent, lead/activity and delivery event commit together. No email is sent by this endpoint. "
        + "status SUBMITTED means saved; deliveryStatus PENDING/SENT/FAILED describes broker publication. SENT does not "
        + "prove consumer storage or email delivery. Publisher is opt-in through CONTACT_REQUEST_PUBLISHER_ENABLED.")
    public ResponseEntity<ContactResponseDTO> submit(@RequestBody PropertyContactRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var result = contacts.submit(currentUser.getCurrentUserEntity().getId(), request, key);
        if (!result.created()) return ResponseEntity.ok(result.request());
        return ResponseEntity.created(URI.create("/api/contact-requests/" + result.request().id())).body(result.request());
    }

    @GetMapping("/professionals")
    @Operation(summary = "List selectable registered professionals", description = "Authenticated, paginated REALTOR "
        + "accounts, newest ID first; page >=0, size 1-100. Returns only local user ID and display name; no private "
        + "email, phone or Keycloak subject. Account category does not imply professional credential verification.")
    public CrmPageDTO<ContactProfessionalDTO> professionals(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        currentUser.getCurrentUserEntity();
        return contacts.professionals(page, size);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read the saved contact request and delivery status", description = "Only the requester "
        + "or selected professional can read the shared contact details and this status. Historical property/lead references remain after source "
        + "records are removed. For lead details, the professional uses the existing owned CRM endpoints.")
    public ContactResponseDTO get(@PathVariable Long id) {
        return contacts.get(currentUser.getCurrentUserEntity().getId(), id);
    }
}

