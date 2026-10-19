package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.geointelli.ai.property.service.dto.PropertySearchItem;
import com.geointelli.ai.property.service.dto.PropertySearchRequest;
import com.geointelli.ai.property.service.dto.PropertySearchResponse;
import com.geointelli.ai.property.service.entity.Address;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.Property;
import com.geointelli.ai.property.service.entity.State;
import com.geointelli.ai.property.service.service.PropertySearchService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PropertySearchServiceImpl implements PropertySearchService {
    private final EntityManager entityManager;
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "id", "id", "bedrooms", "bedroomCount", "bathrooms", "bathroomCount",
        "livingAreaSqft", "buildingHeatedArea", "lotSize", "lotSize", "yearBuilt", "yearBuilt");

    @Override
    @Transactional(readOnly = true)
    public PropertySearchResponse search(PropertySearchRequest request) {
        validate(request);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(PropertySearchItem.class);
        Root<Property> property = query.from(Property.class);
        var joins = new SearchJoins(property);
        query.select(cb.construct(PropertySearchItem.class,
            property.get("id"), property.get("folio"), joins.county.get("id"), joins.county.get("name"),
            joins.state.get("code"), joins.state.get("name"), joins.address.get("address"),
            joins.address.get("city"), joins.address.get("zip"), property.get("dorCode"),
            property.get("dorDescription"), property.get("bedroomCount"), property.get("bathroomCount"),
            property.get("buildingHeatedArea"), property.get("lotSize"), property.get("yearBuilt"),
            property.get("neighborhood"), property.get("neighborhoodDescription")));
        query.where(predicates(request, property, joins, cb));

        Expression<?> sort = "yearBuilt".equals(request.getSortBy())
            ? validYear(property, cb) : property.get(SORT_FIELDS.get(request.getSortBy()));
        List<Order> order = new ArrayList<>();
        order.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(sort), 1).otherwise(0)));
        order.add("asc".equalsIgnoreCase(request.getDirection()) ? cb.asc(sort) : cb.desc(sort));
        if (!"id".equals(request.getSortBy())) {
            order.add(cb.asc(property.get("id")));
        }
        query.orderBy(order);
        List<PropertySearchItem> content = entityManager.createQuery(query)
            .setFirstResult(Math.toIntExact((long) request.getPage() * request.getSize()))
            .setMaxResults(request.getSize()).getResultList();

        var countQuery = cb.createQuery(Long.class);
        Root<Property> countProperty = countQuery.from(Property.class);
        countQuery.select(cb.count(countProperty));
        countQuery.where(predicates(request, countProperty, new SearchJoins(countProperty), cb));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        long totalPages = (total + request.getSize() - 1) / request.getSize();
        return new PropertySearchResponse(content, request.getPage(), request.getSize(), total,
            totalPages, request.getPage() == 0, (long) request.getPage() + 1 >= totalPages);
    }

    private Predicate[] predicates(PropertySearchRequest r, Root<Property> p, SearchJoins j, CriteriaBuilder cb) {
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
            filters.add(cb.like(cb.lower(j.address.get("address")), "%" + literal + "%", '\\'));
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
        return filters.toArray(Predicate[]::new);
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

    private void validate(PropertySearchRequest request) {
        if (request.getPage() < 0 || request.getSize() < 1 || request.getSize() > 100
                || (long) request.getPage() * request.getSize() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("page must be non-negative, size must be 1-100, and offset must fit an integer");
        }
        if (request.getSortBy() == null || !SORT_FIELDS.containsKey(request.getSortBy())) {
            throw new IllegalArgumentException("sortBy must be id, bedrooms, bathrooms, livingAreaSqft, lotSize or yearBuilt");
        }
        if (!"asc".equalsIgnoreCase(request.getDirection()) && !"desc".equalsIgnoreCase(request.getDirection())) {
            throw new IllegalArgumentException("direction must be asc or desc");
        }
        checkRange("bedrooms", request.getMinBedrooms(), request.getMaxBedrooms());
        checkRange("bathrooms", request.getMinBathrooms(), request.getMaxBathrooms());
        checkRange("living area", request.getMinLivingAreaSqft(), request.getMaxLivingAreaSqft());
        if (request.getMinYearBuilt() != null && request.getMaxYearBuilt() != null && request.getMinYearBuilt() > request.getMaxYearBuilt()) {
            throw new IllegalArgumentException("minYearBuilt must not exceed maxYearBuilt");
        }
    }

    private void checkRange(String field, BigDecimal min, BigDecimal max) {
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)
                || (min != null && max != null && min.compareTo(max) > 0)) {
            throw new IllegalArgumentException("Invalid " + field + " range: require 0 <= minimum <= maximum");
        }
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
