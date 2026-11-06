package com.geointelli.ai.property.service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import com.geointelli.ai.property.service.service.ContactRequestOutboxService;
import lombok.RequiredArgsConstructor;

/** Contact delivery runs independently of alerts.enabled; disabled publishers do not connect to RabbitMQ. */
@Configuration
@ConditionalOnProperty(prefix = "contact-requests", name = "publisher-enabled", havingValue = "true")
@RequiredArgsConstructor
public class ContactRequestDeliveryConfig {
    private static final Logger log = LoggerFactory.getLogger(ContactRequestDeliveryConfig.class);
    private final ContactRequestOutboxService publishing;
    private final ContactRequestProperties settings;

    @Bean
    public Declarables contactRequestDestination() {
        TopicExchange exchange = new TopicExchange(settings.getExchange(), true, false);
        Queue queue = new Queue(settings.getQueue(), true);
        return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange).with(settings.getRoutingKey()));
    }

    @Scheduled(fixedDelayString = "${contact-requests.poll-delay-millis:5000}")
    public void publish() {
        try {
            for (int index = 0; index < settings.getBatchSize(); index++) {
                if (Thread.currentThread().isInterrupted() || !publishing.processNext()) break;
            }
        } catch (RuntimeException error) {
            log.warn("Contact-request publishing failed: {}", error.getClass().getSimpleName());
        }
    }
}

