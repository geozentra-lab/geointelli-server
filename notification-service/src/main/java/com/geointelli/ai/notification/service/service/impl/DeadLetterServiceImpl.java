package com.geointelli.ai.notification.service.service.impl;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import com.geointelli.ai.notification.service.config.NotificationProperties;
import com.geointelli.ai.notification.service.service.DeadLetterService;

/**
 * Keeps original bytes with stable failure metadata; no exception messages, SQL
 * or stack traces are copied.
 */
@Service
public class DeadLetterServiceImpl implements DeadLetterService {
    private final RabbitTemplate rabbit;
    private final NotificationProperties settings;

    public DeadLetterServiceImpl(RabbitTemplate rabbit, NotificationProperties settings) {
        this.rabbit = rabbit;
        this.settings = settings;
    }

    @Override
    public void publish(Message original, String reason, int attempts) {
        Message failed = MessageBuilder.withBody(original.getBody()).setContentType("application/json")
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT).setHeader("failure-code", reason)
                .setHeader("processing-attempts", attempts).setHeader("original-queue", settings.getQueue())
                .setHeader("original-routing-key", settings.getRoutingKey()).build();
        if (original.getMessageProperties().getMessageId() != null)
            failed.getMessageProperties().setMessageId(original.getMessageProperties().getMessageId());
        CorrelationData correlation = new CorrelationData();
        try {
            rabbit.send(settings.getDeadLetterExchange(), settings.getDeadLetterRoutingKey(), failed, correlation);
            CorrelationData.Confirm confirm = correlation.getFuture().get(settings.getConfirmTimeoutSeconds(),
                    TimeUnit.SECONDS);
            if (!confirm.ack() || correlation.getReturned() != null)
                throw new IllegalStateException("DEAD_LETTER_NOT_CONFIRMED");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("DEAD_LETTER_INTERRUPTED", interrupted);
        } catch (Exception failedPublish) {
            throw new IllegalStateException("DEAD_LETTER_PUBLISH_FAILED", failedPublish);
        }
    }
}
