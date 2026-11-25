package com.geointelli.ai.property.service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.geointelli.ai.property.service.dto.RealtorLicenseDecisionRequest;
import com.geointelli.ai.property.service.dto.RealtorLicenseReviewDTO;
import com.geointelli.ai.property.service.entity.RealtorLicenseDecisionMethod;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerification;
import com.geointelli.ai.property.service.entity.RealtorLicenseVerificationStatus;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.repository.RealtorLicenseVerificationRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;

/** Manual decisions on realtor license submissions, including a license that expired while pending. */
class RealtorLicenseReviewServiceImplTest {
    private static final long ID = 41L;
    private static final RealtorLicenseDecisionRequest APPROVE =
        new RealtorLicenseDecisionRequest("Florida DBPR licensee search", null);
    private static final RealtorLicenseDecisionRequest REJECT =
        new RealtorLicenseDecisionRequest("Florida DBPR licensee search", "License expired before review.");

    private RealtorLicenseVerificationRepository repository;
    private RealtorLicenseReviewServiceImpl service;
    private RealtorLicenseVerification row;

    @BeforeEach
    void setUp() {
        repository = mock(RealtorLicenseVerificationRepository.class);
        UserRepository users = mock(UserRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        User admin = new User();
        admin.setId(8L);
        when(currentUser.getCurrentUserEntity()).thenReturn(admin);
        when(users.findById(any())).thenReturn(Optional.empty());
        service = new RealtorLicenseReviewServiceImpl(repository, users, currentUser);

        row = new RealtorLicenseVerification();
        row.setId(ID);
        row.setUserId(5L);
        row.setLicenseHolderName("Test Applicant");
        row.setLicensingState("FL");
        row.setLicenseNumber("SL3456789");
        row.setStatus(RealtorLicenseVerificationStatus.PENDING_REVIEW);
        when(repository.findByIdForUpdate(ID)).thenReturn(Optional.of(row));
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    @Test
    @DisplayName("A license that expired while the submission was pending cannot be approved, and nothing is written")
    void whenExpiredWhilePending_thenApproveRefused() {
        row.setLicenseExpirationDate(today().minusDays(1));

        assertThatThrownBy(() -> service.approve(ID, APPROVE))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(error.getReason()).contains("expired on " + today().minusDays(1)).contains("reject it");
            });
        verify(repository, never()).saveAndFlush(any());
        assertThat(row.getStatus()).isEqualTo(RealtorLicenseVerificationStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("A license expiring today can still be approved (same rule as submission)")
    void whenExpiringToday_thenApproved() {
        row.setLicenseExpirationDate(today());

        RealtorLicenseReviewDTO decided = service.approve(ID, APPROVE);

        assertThat(decided.status()).isEqualTo(RealtorLicenseVerificationStatus.VERIFIED);
        assertThat(decided.decisionMethod()).isEqualTo(RealtorLicenseDecisionMethod.MANUAL);
        assertThat(decided.reviewedByUserId()).isEqualTo(8L);
    }

    @Test
    @DisplayName("A submission without an expiration date is decided on the registry check alone")
    void whenNoExpirationDate_thenApproved() {
        row.setLicenseExpirationDate(null);

        assertThat(service.approve(ID, APPROVE).status()).isEqualTo(RealtorLicenseVerificationStatus.VERIFIED);
    }

    @Test
    @DisplayName("An expired license can still be rejected with a reason")
    void whenExpired_thenRejectAllowed() {
        row.setLicenseExpirationDate(today().minusDays(30));

        RealtorLicenseReviewDTO decided = service.reject(ID, REJECT);

        assertThat(decided.status()).isEqualTo(RealtorLicenseVerificationStatus.REJECTED);
        assertThat(decided.rejectionReason()).isEqualTo("License expired before review.");
    }

    @Test
    @DisplayName("An already decided submission reports that first, even when its license has since expired")
    void whenAlreadyDecided_thenDecidedConflictFirst() {
        row.setStatus(RealtorLicenseVerificationStatus.VERIFIED);
        row.setLicenseExpirationDate(today().minusDays(1));

        assertThatThrownBy(() -> service.approve(ID, APPROVE))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getReason()).contains("already decided"));
        verify(repository, never()).saveAndFlush(any());
    }
}
