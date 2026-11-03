package com.geointelli.ai.notification.service.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import com.geointelli.ai.notification.service.config.NotificationEmailProperties;
import com.geointelli.ai.notification.service.dto.EmailDispatchDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO.Outcome;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;

class SesEmailProviderServiceImplTest {
    private final SesV2Client ses = mock(SesV2Client.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SesV2Client> clients = mock(ObjectProvider.class);
    private final NotificationEmailProperties settings = new NotificationEmailProperties();
    private final EmailDispatchDTO task = new EmailDispatchDTO(1, 1, "owner@example.com", "GeoZentra alert",
            "Plain text");
    private final SesEmailProviderServiceImpl provider = new SesEmailProviderServiceImpl(clients, settings);

    @BeforeEach
    void setup() {
        settings.setFromAddress("sender@example.com");
        when(clients.getObject()).thenReturn(ses);
    }

    @Test
    void sendsOneUtf8EmailAndRetainsSesMessageId() {
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-123").build());
        var result = provider.send(task);
        assertEquals(Outcome.ACCEPTED, result.outcome());
        assertEquals("ses-123", result.providerMessageId());
        var request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        assertEquals("sender@example.com", request.getValue().fromEmailAddress());
        assertEquals("owner@example.com", request.getValue().destination().toAddresses().get(0));
        assertEquals("Plain text", request.getValue().content().simple().body().text().data());
        assertEquals("UTF-8", request.getValue().content().simple().body().text().charset());
    }

    @Test
    void sendsBrandedHtmlWithEscapedContentAndConfiguredInboxLink() {
        settings.setInboxUrl("https://app.example.com/notifications?source=email&view=all");
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-html").build());
        var dispatch = new EmailDispatchDTO(2, 1, "owner@example.com", "Updates <today>",
                "Search <script>alert(1)</script>\nSecond alert & details");

        assertEquals(Outcome.ACCEPTED, provider.send(dispatch).outcome());

        var request = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(ses).sendEmail(request.capture());
        var body = request.getValue().content().simple().body();
        assertEquals(dispatch.body(), body.text().data());
        String html = body.html().data();
        assertEquals("UTF-8", body.html().charset());
        assertTrue(html.contains("G</span>eo<span style=\"color:#8b5e3c;\">Z</span>entra"));
        assertTrue(html.contains("Updates &lt;today&gt;"));
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;<br>Second alert &amp; details"));
        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("href=\"https://app.example.com/notifications?source=email&amp;view=all\""));
    }

    @Test
    void explicitThrottlingIsRetryable() {
        when(ses.sendEmail(any(SendEmailRequest.class)))
                .thenThrow(TooManyRequestsException.builder().statusCode(429).build());
        assertEquals(Outcome.RETRYABLE_REJECTION, provider.send(task).outcome());
        verify(ses).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void explicitClientRejectionIsTerminal() {
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SesV2Exception.builder().statusCode(400).build());
        assertEquals(Outcome.REJECTED, provider.send(task).outcome());
    }

    @Test
    void serverErrorsAreNotBlindlyRetried() {
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SesV2Exception.builder().statusCode(503).build());
        assertEquals(Outcome.UNKNOWN, provider.send(task).outcome());
    }

    @Test
    void timeoutsHaveUnknownAcceptanceAndMakeOnlyOneAttempt() {
        when(ses.sendEmail(any(SendEmailRequest.class))).thenThrow(SdkClientException.create("timeout"));
        assertEquals(Outcome.UNKNOWN, provider.send(task).outcome());
        verify(ses, times(1)).sendEmail(any(SendEmailRequest.class));
    }
}
