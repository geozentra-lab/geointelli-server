package com.geointelli.ai.property.service.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Declares a durable destination for the notification service. */
@Configuration
@ConditionalOnProperty(prefix = "alerts", name = {"enabled", "publisher.enabled"}, havingValue = "true")
public class AlertRabbitConfig {
    @Bean
    public Declarables alertDestinations(AlertMonitoringProperties settings) {
        TopicExchange exchange = new TopicExchange(settings.getPublisher().getExchange(), true, false);
        Queue queue = new Queue(settings.getPublisher().getQueue(), true);
        return new Declarables(exchange, queue, BindingBuilder.bind(queue).to(exchange)
            .with(settings.getPublisher().getRoutingKey()));
    }
}
