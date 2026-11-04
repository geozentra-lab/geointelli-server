package com.geointelli.ai.notification.service.service;

/**
 * Resolves the notification recipient from the verified authentication context.
 */
public interface CurrentUserService {
    /**
     * Returns the JWT subject, or rejects requests without a usable bearer
     * identity.
     */
    String getSubject();

    /**
     * Returns the trusted token's verified email, or rejects email opt-in without
     * one.
     */
    String getVerifiedEmail();
}
