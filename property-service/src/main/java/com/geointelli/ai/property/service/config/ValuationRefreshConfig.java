package com.geointelli.ai.property.service.config;

import java.util.List;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.jpa.boot.spi.IntegratorProvider;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/** Registers transactional source hooks with this service's Hibernate session factory. */
@Configuration
@RequiredArgsConstructor
public class ValuationRefreshConfig implements HibernatePropertiesCustomizer {
    private final ValuationRefreshListener listener;

    /** Adds source-change listeners without changing global JPA transaction behavior. */
    @Override
    public void customize(Map<String, Object> properties) {
        properties.put("hibernate.integrator_provider", (IntegratorProvider) () -> List.of(new Integrator() {
            @Override
            public void integrate(Metadata metadata, BootstrapContext context, SessionFactoryImplementor factory) {
                var registry = factory.getServiceRegistry().getService(EventListenerRegistry.class);
                registry.appendListeners(EventType.POST_INSERT, listener);
                registry.appendListeners(EventType.POST_UPDATE, listener);
                registry.appendListeners(EventType.POST_DELETE, listener);
            }

            @Override
            public void disintegrate(SessionFactoryImplementor factory, SessionFactoryServiceRegistry registry) {
                // Listeners are owned by this session factory and need no separate cleanup.
            }
        }));
    }
}
