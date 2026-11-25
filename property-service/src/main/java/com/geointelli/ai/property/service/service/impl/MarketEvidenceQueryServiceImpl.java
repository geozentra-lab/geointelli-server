package com.geointelli.ai.property.service.service.impl;

import java.time.LocalDate;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.MarketEvidenceObservationDTO;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.entity.MarketEvidenceType;
import com.geointelli.ai.property.service.entity.MarketGeographyLevel;
import com.geointelli.ai.property.service.repository.MarketEvidenceObservationRepository;
import com.geointelli.ai.property.service.service.MarketEvidenceQueryService;
import lombok.RequiredArgsConstructor;

/**
 * Validates the market and the paging, then reads the page through
 * {@link MarketEvidenceObservationRepository#findMarket}. Codes are normalised the way the importer stores
 * them (state codes upper-cased), so a lower-case query still finds its rows.
 */
@Service
@RequiredArgsConstructor
public class MarketEvidenceQueryServiceImpl implements MarketEvidenceQueryService {
    static final int MAX_PAGE_SIZE = 200;
    private static final Pattern STATE_FORMAT = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern FIVE_DIGITS = Pattern.compile("\\d{5}");

    private final MarketEvidenceObservationRepository observations;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MarketEvidenceObservationDTO> find(MarketGeographyLevel level, String code,
            MarketEvidenceType type, String source, LocalDate from, LocalDate to, int page, int size) {
        if (level == null) throw new IllegalArgumentException("level is required");
        String normalisedCode = normaliseCode(level, code);
        String normalisedSource = source == null || source.isBlank() ? null : source.trim().toUpperCase(Locale.ROOT);
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (page < 0) throw new IllegalArgumentException("page must be 0 or more");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageResponse.from(observations
            .findMarket(level, normalisedCode, type, normalisedSource, from, to, PageRequest.of(page, size))
            .map(MarketEvidenceObservationDTO::from));
    }

    private static String normaliseCode(MarketGeographyLevel level, String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        String value = code.trim();
        return switch (level) {
            case STATE -> {
                if (!STATE_FORMAT.matcher(value).matches()) {
                    throw new IllegalArgumentException("code: a STATE market needs a two-letter state code");
                }
                yield value.toUpperCase(Locale.ROOT);
            }
            case COUNTY -> {
                if (!FIVE_DIGITS.matcher(value).matches()) {
                    throw new IllegalArgumentException("code: a COUNTY market needs a five-digit FIPS code");
                }
                yield value;
            }
            case ZIP -> {
                if (!FIVE_DIGITS.matcher(value).matches()) {
                    throw new IllegalArgumentException("code: a ZIP market needs a five-digit ZIP code");
                }
                yield value;
            }
        };
    }
}
