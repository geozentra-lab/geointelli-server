package com.geointelli.ai.property.service.config;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;

import org.hibernate.event.spi.EventSource;
import org.hibernate.event.spi.PostInsertEvent;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.jdbc.Work;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.PropertyValuePrediction;
import com.geointelli.ai.property.service.entity.Sale;
import com.geointelli.ai.property.service.service.ValuationRefreshService;

class ValuationRefreshListenerTest {
    private final ValuationRefreshService refresh = mock(ValuationRefreshService.class);
    private final EntityPersister persister = mock(EntityPersister.class);
    private final EventSource session = mock(EventSource.class);
    private final Connection connection = mock(Connection.class);
    private final ValuationRefreshListener listener = new ValuationRefreshListener(refresh,true);

    @BeforeEach
    void prepare() {
        doAnswer(call -> { call.getArgument(0,Work.class).execute(connection); return null; }).when(session).doWork(any(Work.class));
    }

    @Test
    void saleInsertUsesTheSourceSessionConnection() throws Exception {
        var property = new Property(); property.setId(301400L);
        when(persister.getMappedClass()).thenReturn((Class) Sale.class);
        when(persister.getPropertyNames()).thenReturn(new String[] {"property"});
        listener.onPostInsert(new PostInsertEvent(new Sale(),91L,new Object[] {property},persister,session));
        verify(refresh).sourceChanged(connection,"SALE_CHANGE",301400L,91L);
    }

    @Test
    void updateRefreshesBothOwnersWhenSaleMoves() throws Exception {
        var old = new Property(); old.setId(1L);
        var current = new Property(); current.setId(2L);
        when(persister.getMappedClass()).thenReturn((Class) Sale.class);
        when(persister.getPropertyNames()).thenReturn(new String[] {"property"});
        listener.onPostUpdate(new PostUpdateEvent(new Sale(),91L,new Object[] {current},new Object[] {old},
            new int[] {0},persister,session));
        verify(refresh).sourceChanged(connection,"SALE_CHANGE",1L,91L);
        verify(refresh).sourceChanged(connection,"SALE_CHANGE",2L,91L);
    }

    @Test
    void auditOrMessageUpdateDoesNotRecomputeValues() {
        when(persister.getMappedClass()).thenReturn((Class) Property.class);
        when(persister.getPropertyNames()).thenReturn(new String[] {"message","updatedAt","bedroomCount"});
        listener.onPostUpdate(new PostUpdateEvent(new Property(),1L,new Object[3],new Object[3],
            new int[] {0,1},persister,session));
        verifyNoInteractions(refresh);
    }

    @Test
    void modelPublicationAndDisabledHooksNeverEnqueueFeedbackLoops() {
        when(persister.getMappedClass()).thenReturn((Class) PropertyValuePrediction.class);
        assertThatThrownBy(() -> listener.onPostInsert(
            new PostInsertEvent(new PropertyValuePrediction(),1L,new Object[0],persister,session)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("published by Python");
        new ValuationRefreshListener(refresh,false).onPostInsert(
            new PostInsertEvent(new PropertyValuePrediction(),1L,new Object[0],persister,session));
        verifyNoInteractions(refresh);
    }
}
