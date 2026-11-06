package com.geointelli.ai.property.service.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import com.geointelli.ai.property.service.config.ContactRequestProperties;
import com.geointelli.ai.property.service.service.ContactRequestEventPublisher;
import lombok.RequiredArgsConstructor;

/** Persistent, mandatory publication reuses the existing RabbitTemplate and connection configuration. */
@Service
@RequiredArgsConstructor
public class RabbitContactRequestEventPublisherImpl implements ContactRequestEventPublisher {
    private final RabbitTemplate rabbit;
    private final ContactRequestProperties settings;

    @Override
    public void publish(String eventId, String eventJson) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(eventId);
        properties.setType("CONTACT_REQUEST");
        properties.setAppId("property-service");
        CorrelationData correlation = new CorrelationData(eventId);
        rabbit.send(settings.getExchange(), settings.getRoutingKey(),
            new Message(eventJson.getBytes(StandardCharsets.UTF_8), properties), correlation);
        var confirm = correlation.getFuture().get(settings.getConfirmTimeoutSeconds(), TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null)
            throw new IllegalStateException("BROKER_DELIVERY_FAILED");
    }
}

