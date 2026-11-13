package com.geointelli.ai.property.service.config;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.PostDeleteEvent;
import org.hibernate.event.spi.PostDeleteEventListener;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostInsertEventListener;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.hibernate.persister.entity.EntityPersister;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

import com.geointelli.ai.property.service.entity.Assessment;
import com.geointelli.ai.property.service.entity.Building;
import com.geointelli.ai.property.service.entity.Land;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.entity.Sale;
import com.geointelli.ai.property.service.service.ValuationRefreshService;

/**
 * Observes model-input ORM changes during flush, before the source transaction commits.
 * After cutover, rejects legacy ORM prediction writes that would bypass Python's atomic publication.
 */
@Component
public class ValuationRefreshListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener {
    private static final Map<Class<?>, Set<String>> INPUTS = Map.of(
        Property.class, Set.of("bedroomCount", "bathroomCount", "buildingHeatedArea", "buildingGrossArea", "lotSize",
            "yearBuilt", "municipality", "neighborhood", "dorCode", "dorDescription", "county", "parentProperty"),
        Sale.class, Set.of("property", "saleDate", "salePrice", "qualifiedFlag", "instrumentNumber",
            "qualificationDescription", "saleType", "vacantFlag"),
        Building.class, Set.of("property", "actual", "effective", "segNo", "percentComp",
            "percentageGood", "grossArea", "buildingNo", "heatedArea", "rollYear", "adjustedBasePrice",
            "replacementCostNew", "depreciatedValue"),
        Land.class, Set.of("property", "zone", "landUse", "adjustedUnitPrice", "calculatedValue", "units"),
        Assessment.class, Set.of("property", "year", "buildingOnlyValue", "landValue"),
        Parcel.class, Set.of("property", "geom"));

    private final ValuationRefreshService refresh;
    private final boolean enabled;

    /** Enables source hooks only after the shared schema and Python worker are ready. */
    public ValuationRefreshListener(@Lazy ValuationRefreshService refresh,
            @Value("${valuation.refresh.enabled:false}") boolean enabled) {
        this.refresh = refresh;
        this.enabled = enabled;
    }

    /** Enqueues inserts of model input records, excluding predictions and unrelated tables. */
    @Override
    public void onPostInsert(PostInsertEvent event) {
        changed(event.getEntity(), event.getId(), event.getState(), null, event.getPersister(), event.getSession());
    }

    /** Enqueues only changed model inputs; audit timestamps and metadata do not trigger scoring. */
    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        if (!enabled) return;
        requirePythonPublisher(event.getEntity());
        Set<String> inputs = INPUTS.get(event.getPersister().getMappedClass());
        if (inputs == null) return;
        int[] dirty = event.getDirtyProperties();
        String[] names = event.getPersister().getPropertyNames();
        if (dirty != null && Arrays.stream(dirty).noneMatch(index -> inputs.contains(names[index]))) return;
        changed(event.getEntity(), event.getId(), event.getState(), event.getOldState(),
            event.getPersister(), event.getSession());
    }

    /** Enqueues source removal as well, so removed/invalidated sales cannot leave a stale estimate. */
    @Override
    public void onPostDelete(PostDeleteEvent event) {
        changed(event.getEntity(), event.getId(), event.getDeletedState(), null, event.getPersister(), event.getSession());
    }

    /** Uses in-transaction events, never post-commit callbacks with a separate transaction. */
    @Override
    public boolean requiresPostCommitHandling(EntityPersister persister) {
        return false;
    }

    private void changed(Object entity, Object id, Object[] state, Object[] previous,
            EntityPersister persister, EventSource session) {
        if (!enabled) return;
        requirePythonPublisher(entity);
        if (!INPUTS.containsKey(persister.getMappedClass())) return;
        Long propertyId = entity instanceof Property ? (Long) id : owner(state, persister);
        Long oldPropertyId = previous == null ? null : owner(previous, persister);
        String trigger = entity instanceof Sale ? "SALE_CHANGE" : "PROPERTY_CHANGE";
        Long saleId = entity instanceof Sale ? (Long) id : null;
        if (propertyId != null) session.doWork(connection -> refresh.sourceChanged(connection, trigger, propertyId, saleId));
        if (oldPropertyId != null && !oldPropertyId.equals(propertyId))
            session.doWork(connection -> refresh.sourceChanged(connection, trigger, oldPropertyId, saleId));
    }

    private void requirePythonPublisher(Object entity) {
        if (entity instanceof PropertyValuePrediction)
            throw new IllegalStateException("Valuations must be published by Python; legacy prediction imports bypass immutable history");
    }

    private Long owner(Object[] state, EntityPersister persister) {
        if (state == null) return null;
        String[] names = persister.getPropertyNames();
        for (int index = 0; index < names.length; index++) {
            if ("property".equals(names[index]) && state[index] instanceof Property property) return property.getId();
        }
        return null;
    }
}
