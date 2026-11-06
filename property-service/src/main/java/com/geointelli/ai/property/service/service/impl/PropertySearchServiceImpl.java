package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.locationtech.jts.geom.Geometry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Parcel;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.State;
import com.geointelli.ai.property.service.service.PropertySearchService;
import com.geointelli.ai.property.service.service.PropertySearchValidationService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CommonAbstractCriteria;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

/**
 * {@link PropertySearchService} built on JPA Criteria queries: every filter given in the request
 * becomes one SQL predicate, and the same predicates drive both the page query and the count query.
 */
@Service
@RequiredArgsConstructor
public class PropertySearchServiceImpl implements PropertySearchService {
    private static final int WGS84_SRID = 4326;
    /**
     * Above this many matches an ID-sorted page walks the primary key and stops at the first page of hits.
     * Below it, matches may sit far into the key, so the planner must collect and top-N sort them instead.
     */
    private static final long INDEX_ORDER_MIN_MATCHES = 10_000;
    private final EntityManager entityManager;
    private final PropertySearchValidationService searchValidation;
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "id", "id", "bedrooms", "bedroomCount", "bathrooms", "bathroomCount",
        "livingAreaSqft", "buildingHeatedArea", "lotSize", "lotSize", "yearBuilt", "yearBuilt");

    @Override
    @Transactional(readOnly = true)
    public PropertySearchResponse search(PropertySearchRequest request) {
        searchValidation.validate(request);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var countQuery = cb.createQuery(Long.class);
        Root<Property> countProperty = countQuery.from(Property.class);
        countQuery.select(cb.count(countProperty));
        countQuery.where(predicates(request, countQuery, countProperty, new SearchJoins(countProperty), cb));
        long total = entityManager.createQuery(countQuery).getSingleResult();

        long offset = (long) request.getPage() * request.getSize();
        List<PropertySearchItem> content = offset < total ? page(request, total, cb) : List.of();
        long totalPages = (total + request.getSize() - 1) / request.getSize();
        return new PropertySearchResponse(content, request.getPage(), request.getSize(), total,
            totalPages, request.getPage() == 0, (long) request.getPage() + 1 >= totalPages);
    }

    private List<PropertySearchItem> page(PropertySearchRequest request, long total, CriteriaBuilder cb) {
        var query = cb.createQuery(PropertySearchItem.class);
        Root<Property> property = query.from(Property.class);
        var joins = new SearchJoins(property);
        selectCard(query, property, joins, cb);
        query.where(predicates(request, query, property, joins, cb));

        boolean ascending = "asc".equalsIgnoreCase(request.getDirection());
        List<Order> order = new ArrayList<>();
        if ("id".equals(request.getSortBy()) && total > INDEX_ORDER_MIN_MATCHES) {
            // IDs are never null, so a bare ID order returns the same rows and can use the primary key.
            order.add(ascending ? cb.asc(property.get("id")) : cb.desc(property.get("id")));
        } else {
            Expression<?> sort = "yearBuilt".equals(request.getSortBy())
                ? validYear(property, cb) : property.get(SORT_FIELDS.get(request.getSortBy()));
            order.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(sort), 1).otherwise(0)));
            order.add(ascending ? cb.asc(sort) : cb.desc(sort));
            if (!"id".equals(request.getSortBy())) {
                order.add(cb.asc(property.get("id")));
            }
        }
        query.orderBy(order);
        return entityManager.createQuery(query)
            .setFirstResult(Math.toIntExact((long) request.getPage() * request.getSize()))
            .setMaxResults(request.getSize()).getResultList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PropertySearchItem> findRecommendationCandidates(List<Long> countyIds, int limit) {
        if (countyIds == null || countyIds.isEmpty() || countyIds.size() > 20
                || countyIds.stream().anyMatch(id -> id == null || id <= 0)
                || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Expected 1 to 20 county IDs and a limit between 1 and 50");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PropertySearchItem> query = cb.createQuery(PropertySearchItem.class);
        Root<Property> property = query.from(Property.class);
        SearchJoins joins = new SearchJoins(property);
        selectCard(query, property, joins, cb);
        query.where(joins.county.get("id").in(countyIds));
        query.orderBy(cb.asc(property.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PropertySearchItem> findCards(List<Long> propertyIds) {
        if (propertyIds.size() > 100) {
            throw new IllegalArgumentException("At most 100 property IDs can be loaded at once");
        }
        if (propertyIds.isEmpty()) return List.of();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PropertySearchItem> query = cb.createQuery(PropertySearchItem.class);
        Root<Property> property = query.from(Property.class);
        selectCard(query, property, new SearchJoins(property), cb);
        query.where(property.get("id").in(propertyIds));
        Map<Long, PropertySearchItem> byId = new HashMap<>();
        entityManager.createQuery(query).getResultList().forEach(card -> byId.put(card.id(), card));
        return propertyIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Keeps search, recommendations and address search on the same lightweight property-card contract. */
    private void selectCard(CriteriaQuery<PropertySearchItem> query, Root<Property> property,
            SearchJoins joins, CriteriaBuilder cb) {
        query.select(cb.construct(PropertySearchItem.class,
            property.get("id"), property.get("folio"), joins.county.get("id"), joins.county.get("name"),
            joins.state.get("code"), joins.state.get("name"), joins.address.get("address"),
            joins.address.get("city"), joins.address.get("zip"), property.get("dorCode"),
            property.get("dorDescription"), property.get("bedroomCount"), property.get("bathroomCount"),
            property.get("buildingHeatedArea"), property.get("lotSize"), property.get("yearBuilt"),
            property.get("neighborhood"), property.get("neighborhoodDescription")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findMatchingIds(PropertySearchRequest request, long afterPropertyId, int limit) {
        searchValidation.validate(request);
        if (afterPropertyId < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Invalid property ID cursor or batch size");
        }
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Long.class);
        Root<Property> property = query.from(Property.class);
        query.select(property.get("id")).distinct(true);
        query.where(cb.and(predicates(request, query, property, new SearchJoins(property), cb)),
            cb.greaterThan(property.get("id"), afterPropertyId));
        query.orderBy(cb.asc(property.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    private Predicate[] predicates(PropertySearchRequest r, CommonAbstractCriteria q, Root<Property> p,
            SearchJoins j, CriteriaBuilder cb) {
        List<Predicate> filters = new ArrayList<>();
        if (r.getCountyId() != null) filters.add(cb.equal(j.county.get("id"), r.getCountyId()));
        if (hasText(r.getState())) {
            String state = normalized(r.getState());
            filters.add(cb.or(cb.equal(cb.lower(j.state.get("code")), state),
                cb.equal(cb.lower(j.state.get("name")), state)));
        }
        equalText(filters, cb, j.address.get("city"), r.getCity());
        equalText(filters, cb, j.address.get("zip"), r.getZip());
        equalText(filters, cb, p.get("dorDescription"), r.getPropertyType());
        equalText(filters, cb, p.get("dorCode"), r.getDorCode());
        if (hasText(r.getAddress())) {
            String literal = normalized(r.getAddress()).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_");
            // ILIKE on the bare column shares the trigram index used by address suggestions.
            filters.add(((HibernateCriteriaBuilder) cb).ilike(j.address.get("address"), "%" + literal + "%", '\\'));
        }
        if (hasText(r.getNeighborhood())) {
            String neighborhood = normalized(r.getNeighborhood());
            filters.add(cb.or(cb.equal(cb.lower(p.get("neighborhood")), neighborhood),
                cb.equal(cb.lower(p.get("neighborhoodDescription")), neighborhood)));
        }
        range(filters, cb, p.get("bedroomCount"), r.getMinBedrooms(), r.getMaxBedrooms());
        range(filters, cb, p.get("bathroomCount"), r.getMinBathrooms(), r.getMaxBathrooms());
        range(filters, cb, p.get("buildingHeatedArea"), r.getMinLivingAreaSqft(), r.getMaxLivingAreaSqft());
        if (r.getMinYearBuilt() != null || r.getMaxYearBuilt() != null) {
            Expression<String> year = validYear(p, cb);
            if (r.getMinYearBuilt() != null) filters.add(cb.greaterThanOrEqualTo(year, year(r.getMinYearBuilt())));
            if (r.getMaxYearBuilt() != null) filters.add(cb.lessThanOrEqualTo(year, year(r.getMaxYearBuilt())));
        }
        if (r.getMinLotSize() != null) filters.add(cb.ge(p.<Double>get("lotSize"), r.getMinLotSize().doubleValue()));
        if (r.getMaxLotSize() != null) filters.add(cb.le(p.<Double>get("lotSize"), r.getMaxLotSize().doubleValue()));
        if (r.getMinLon() != null) filters.add(inBoundingBox(r, q, p, cb));
        return filters.toArray(Predicate[]::new);
    }

    /**
     * Matches properties having at least one parcel whose geometry intersects the request's bounding box.
     * Properties without parcel geometry never match.
     */
    private Predicate inBoundingBox(PropertySearchRequest r, CommonAbstractCriteria q, Root<Property> p,
            CriteriaBuilder cb) {
        Expression<Geometry> box = cb.function("st_makeenvelope", Geometry.class,
            cb.literal(r.getMinLon().doubleValue()), cb.literal(r.getMinLat().doubleValue()),
            cb.literal(r.getMaxLon().doubleValue()), cb.literal(r.getMaxLat().doubleValue()), cb.literal(WGS84_SRID));
        Subquery<Long> parcels = q.subquery(Long.class);
        Root<Parcel> parcel = parcels.from(Parcel.class);
        parcels.select(parcel.<Long>get("id")).where(cb.equal(parcel.get("property"), p),
            cb.isTrue(cb.function("st_intersects", Boolean.class, parcel.get("geom"), box)));
        return cb.exists(parcels);
    }

    private Expression<String> validYear(Root<Property> p, CriteriaBuilder cb) {
        Expression<String> value = cb.trim(p.<String>get("yearBuilt"));
        Expression<String> unmatched = cb.function("regexp_replace", String.class,
            value, cb.literal("^[0-9]{4}$"), cb.literal(""));
        return cb.<String>selectCase().when(cb.and(cb.equal(cb.length(value), 4),
            cb.equal(unmatched, ""), cb.notEqual(value, "0000")), value).otherwise(cb.nullLiteral(String.class));
    }

    private void equalText(List<Predicate> filters, CriteriaBuilder cb, Expression<String> field, String value) {
        if (hasText(value)) filters.add(cb.equal(cb.lower(field), normalized(value)));
    }

    private void range(List<Predicate> filters, CriteriaBuilder cb, Expression<BigDecimal> field,
            BigDecimal min, BigDecimal max) {
        if (min != null) filters.add(cb.greaterThanOrEqualTo(field, min));
        if (max != null) filters.add(cb.lessThanOrEqualTo(field, max));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String normalized(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private String year(int value) { return String.format(Locale.ROOT, "%04d", value); }

    private static final class SearchJoins {
        final Join<Property, Address> address;
        final Join<Property, County> county;
        final Join<County, State> state;

        SearchJoins(Root<Property> property) {
            address = property.join("address", JoinType.LEFT);
            county = property.join("county", JoinType.LEFT);
            state = county.join("state", JoinType.LEFT);
        }
    }
}
