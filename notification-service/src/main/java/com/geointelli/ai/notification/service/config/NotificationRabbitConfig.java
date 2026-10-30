package com.geointelli.ai.notification.service.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.geointelli.ai.notification.service.exception.InvalidAlertEventException;
import com.geointelli.ai.notification.service.service.DeadLetterService;

/**
 * Does not add immutable DLX arguments to the producer's existing queue.
 * Recovery is a confirmed application publish.
 */
@Configuration
public class NotificationRabbitConfig {
    @Bean
    public Declarables notificationQueues(NotificationProperties settings) {
        TopicExchange main = new TopicExchange(settings.getExchange(), true, false);
        Queue queue = new Queue(settings.getQueue(), true);
        DirectExchange dead = new DirectExchange(settings.getDeadLetterExchange(), true, false);
        Queue dlq = new Queue(settings.getDeadLetterQueue(), true);
        return new Declarables(main, queue, BindingBuilder.bind(queue).to(main).with(settings.getRoutingKey()),
                dead, dlq, BindingBuilder.bind(dlq).to(dead).with(settings.getDeadLetterRoutingKey()));
    }

    @Bean
    public MethodInterceptor notificationRetryAdvice(NotificationProperties settings, DeadLetterService deadLetters) {
        return RetryInterceptorBuilder.stateless().maxRetries(settings.getMaxAttempts() - 1)
                .backOffOptions(settings.getRetryInitialMillis(), 2.0, settings.getRetryMaxMillis())
                .recoverer((message, cause) -> {
                    String code = "PROCESSING_FAILED";
                    Throwable current = cause;
                    while (current != null) {
                        if (current instanceof InvalidAlertEventException invalid) {
                            code = invalid.getMessage();
                            break;
                        }
                        current = current.getCause();
                    }
                    try {
                        deadLetters.publish(message, code, settings.getMaxAttempts());
                    } catch (RuntimeException unavailable) {
                        throw new ImmediateRequeueAmqpException("Dead-letter destination unavailable", unavailable);
                    }
                }).build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory notificationListenerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory,
            @Qualifier("notificationRetryAdvice") MethodInterceptor advice) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(true);
        factory.setAdviceChain(advice);
        return factory;
    }
}
