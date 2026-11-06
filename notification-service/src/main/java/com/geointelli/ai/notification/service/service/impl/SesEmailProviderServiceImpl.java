package com.geointelli.ai.notification.service.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import com.geointelli.ai.notification.service.config.NotificationEmailProperties;
import com.geointelli.ai.notification.service.dto.EmailDispatchDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO;
import com.geointelli.ai.notification.service.dto.EmailSendResultDTO.Outcome;
import com.geointelli.ai.notification.service.service.EmailProviderService;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;
import software.amazon.awssdk.services.sesv2.model.TooManyRequestsException;

/**
 * SES acceptance and mailbox delivery are different. Timeouts and server errors
 * remain UNKNOWN.
 */
@Service
public class SesEmailProviderServiceImpl implements EmailProviderService {
    private static final Logger log = LoggerFactory.getLogger(SesEmailProviderServiceImpl.class);
    private final ObjectProvider<SesV2Client> client;
    private final NotificationEmailProperties settings;

    public SesEmailProviderServiceImpl(ObjectProvider<SesV2Client> client, NotificationEmailProperties settings) {
        this.client = client;
        this.settings = settings;
    }

    @Override
    public EmailSendResultDTO send(EmailDispatchDTO dispatch) {
        try {
            SendEmailRequest request = SendEmailRequest.builder().fromEmailAddress(settings.getFromAddress())
                    .destination(Destination.builder().toAddresses(dispatch.recipient()).build())
                    .content(EmailContent.builder().simple(Message.builder()
                            .subject(Content.builder().data(dispatch.subject()).charset("UTF-8").build())
                            .body(Body.builder().text(Content.builder().data(dispatch.body()).charset("UTF-8").build())
                                    .html(Content.builder().data(htmlBody(dispatch)).charset("UTF-8").build())
                                    .build())
                            .build()).build())
                    .build();
            String id = client.getObject().sendEmail(request).messageId();
            return new EmailSendResultDTO(Outcome.ACCEPTED, id, null);
        } catch (TooManyRequestsException rejected) {
            logSesFailure(rejected);
            return new EmailSendResultDTO(Outcome.RETRYABLE_REJECTION, null, "SES_THROTTLED");
        } catch (SesV2Exception error) {
            logSesFailure(error);
            if (error.statusCode() >= 400 && error.statusCode() < 500)
                return new EmailSendResultDTO(Outcome.REJECTED, null, "SES_REJECTED");
            return new EmailSendResultDTO(Outcome.UNKNOWN, null, "SES_OUTCOME_UNKNOWN");
        } catch (RuntimeException error) {
            log.warn("SES send outcome unknown: exceptionType={}", error.getClass().getSimpleName());
            return new EmailSendResultDTO(Outcome.UNKNOWN, null, "SES_OUTCOME_UNKNOWN");
        }
    }

    /** Table layout and inline styles support email clients; dynamic content is always escaped. */
    private String htmlBody(EmailDispatchDTO dispatch) {
        String subject = HtmlUtils.htmlEscape(dispatch.subject());
        String body = HtmlUtils.htmlEscape(dispatch.body()).replace("\r\n", "\n")
                .replace("\r", "\n").replace("\n", "<br>");
        String url = HtmlUtils.htmlEscape(settings.getInboxUrl());
        return """
                <!doctype html>
                <html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
                <body style="margin:0;padding:0;background-color:#f6f3ef;color:#29241f;font-family:Arial,Helvetica,sans-serif;">
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background-color:#f6f3ef;">
                  <tr><td align="center" style="padding:40px 16px;">
                    <table role="presentation" width="600" cellspacing="0" cellpadding="0" style="width:100%%;max-width:600px;background-color:#ffffff;border:1px solid #e7dfd6;border-radius:12px;">
                      <tr><td style="padding:32px 32px 24px;border-bottom:1px solid #eee7df;">
                        <strong style="font-size:30px;letter-spacing:-1px;color:#29241f;"><span style="color:#8b5e3c;">G</span>eo<span style="color:#8b5e3c;">Z</span>entra</strong>
                      </td></tr>
                      <tr><td style="padding:32px;">
                        <p style="margin:0 0 12px;color:#8b5e3c;font-size:12px;font-weight:bold;letter-spacing:2px;">YOUR PROPERTY UPDATES</p>
                        <h1 style="margin:0 0 20px;font-size:24px;line-height:1.3;">%s</h1>
                        <p style="margin:0 0 28px;font-size:16px;line-height:1.7;color:#51483f;">%s</p>
                        <table role="presentation" cellspacing="0" cellpadding="0"><tr><td bgcolor="#8b5e3c" style="border-radius:6px;">
                          <a href="%s" style="display:inline-block;padding:15px 24px;border:1px solid #8b5e3c;border-radius:6px;color:#ffffff;text-decoration:none;font-size:15px;font-weight:bold;">View notifications &rarr;</a>
                        </td></tr></table>
                        <p style="margin:24px 0 0;font-size:12px;line-height:1.6;color:#756a60;">If the button does not work, open:<br><a href="%s" style="color:#8b5e3c;word-break:break-all;">%s</a></p>
                      </td></tr>
                      <tr><td style="padding:24px 32px;border-top:1px solid #eee7df;font-size:12px;line-height:1.6;color:#756a60;">
                        You received this email because you enabled GeoZentra email notifications.
                        You can change your email preferences in the app.
                      </td></tr>
                    </table>
                  </td></tr>
                </table></body></html>
                """.formatted(subject, body, url, url, url);
    }

    /** Logs AWS diagnostics; provider messages may include identity ARNs and email addresses. */
    private void logSesFailure(SesV2Exception error) {
        String code = error.awsErrorDetails() == null ? "unavailable" : error.awsErrorDetails().errorCode();
        String message = error.awsErrorDetails() == null ? "unavailable" : error.awsErrorDetails().errorMessage();
        if (message != null) message = message.replace('\r', ' ').replace('\n', ' ');
        log.warn("SES send failed: errorCode={}, httpStatus={}, requestId={}, awsMessage={}",
                code, error.statusCode(), error.requestId(), message);
    }
}
