package com.geointelli.ai.property.service.controller;

import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.RealtorLicenseSubmissionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseVerificationDTO;
import com.geointelli.ai.property.service.service.RealtorLicenseVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Current user's realtor license submissions; the owner is the bearer token, never a body field. */
@RestController
@RequestMapping("/api/users/me/realtor-license")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Realtor license", description = "Applicant side of license verification (SCRUM-200). Missing identity 401, "
    + "invalid input 400, no submission yet 404, submission already pending or verified 409. Decisions are admin-only "
    + "and live in SCRUM-199.")
public class RealtorLicenseVerificationController {
    private final RealtorLicenseVerificationService verifications;

    @PostMapping
    @Operation(summary = "Submit my license for verification", description = "licenseHolderName (1-160), licensingState "
        + "(two letters, only FL accepted) and licenseNumber (1-40) are required; licenseExpirationDate (yyyy-MM-dd, not "
        + "in the past), brokerageName (max 160) and workEmail (valid e-mail) are optional. Creates a PENDING_REVIEW "
        + "submission (201). Refused with 409 while a submission is pending or verified; allowed again after a rejection, "
        + "as a new record that keeps the rejected one in history.")
    public ResponseEntity<RealtorLicenseVerificationDTO> submit(@Valid @RequestBody RealtorLicenseSubmissionRequest request) {
        var result = verifications.submit(request);
        return ResponseEntity.created(URI.create("/api/users/me/realtor-license")).body(result);
    }

    @GetMapping
    @Operation(summary = "Read my latest submission", description = "Newest submission with status PENDING_REVIEW, "
        + "VERIFIED or REJECTED and, when rejected, the reason. 404 when I never submitted a license.")
    public RealtorLicenseVerificationDTO latest() { return verifications.getLatest(); }

    @GetMapping("/history")
    @Operation(summary = "List all my submissions", description = "Newest first, including rejected ones. "
        + "Empty list when I never submitted a license.")
    public List<RealtorLicenseVerificationDTO> history() { return verifications.getHistory(); }
}
