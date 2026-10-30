package com.geointelli.ai.property.service.service.impl;

import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.config.AlertMonitoringProperties;
import com.geointelli.ai.property.service.dto.AlertEventEnvelopeDTO;
import com.geointelli.ai.property.service.service.AlertEventPublisher;

/** Uses persistent messages, mandatory routing and correlated publisher confirms. */
@Service
public class RabbitAlertEventPublisherImpl implements AlertEventPublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final AlertMonitoringProperties settings;
    public RabbitAlertEventPublisherImpl(RabbitTemplate rabbit, ObjectMapper json, AlertMonitoringProperties settings) {
        this.rabbit = rabbit; this.json = json; this.settings = settings;
    }
    @Override
    public void publish(AlertEventEnvelopeDTO event) throws Exception {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.eventId());
        properties.setType(event.eventType().name());
        properties.setAppId("property-service");
        CorrelationData correlation = new CorrelationData(event.eventId());
        rabbit.send(settings.getPublisher().getExchange(), settings.getPublisher().getRoutingKey(),
            new Message(json.writeValueAsBytes(event), properties), correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(
            settings.getPublisher().getConfirmTimeoutSeconds(), TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("BROKER_DELIVERY_FAILED");
        }
    }
}
