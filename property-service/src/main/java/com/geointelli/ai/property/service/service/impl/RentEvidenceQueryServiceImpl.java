package com.geointelli.ai.property.service.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.PageResponse;
import com.geointelli.ai.property.service.dto.RentEvidenceObservationDTO;
import com.geointelli.ai.property.service.dto.RentEvidenceResponseDTO;
import com.geointelli.ai.property.service.dto.RentEvidenceResponseDTO.Coverage;
import com.geointelli.ai.property.service.entity.RentEvidenceObservation;
import com.geointelli.ai.property.service.entity.RentEvidenceType;
import com.geointelli.ai.property.service.entity.RentGeographyLevel;
import com.geointelli.ai.property.service.entity.RentPeriod;
import com.geointelli.ai.property.service.repository.RentEvidenceObservationRepository;
import com.geointelli.ai.property.service.service.RentEvidenceQueryService;
import lombok.RequiredArgsConstructor;

/**
 * Validates the geography and the paging, reads the page through
 * {@link RentEvidenceObservationRepository#findGeography}, and checks each requested rent type separately to
 * report coverage. Codes are normalised the way the importer stores them (state codes upper-cased), so a
 * lower-case query still finds its rows. The only conversion is the explicit ANNUAL to monthly one, by 12.
 */
@Service
@RequiredArgsConstructor
public class RentEvidenceQueryServiceImpl implements RentEvidenceQueryService {
    static final int MAX_PAGE_SIZE = 200;
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final Pattern STATE_FORMAT = Pattern.compile("[A-Za-z]{2}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private final RentEvidenceObservationRepository observations;

    @Override
    @Transactional(readOnly = true)
    public RentEvidenceResponseDTO find(RentGeographyLevel level, String code, RentEvidenceType type, String source,
            RentPeriod rentPeriod, LocalDate from, LocalDate to, boolean asMonthly, int page, int size) {
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

        PageResponse<RentEvidenceObservationDTO> rows = PageResponse.from(observations
            .findGeography(level, normalisedCode, type, normalisedSource, rentPeriod, from, to, PageRequest.of(page, size))
            .map(row -> toDto(row, asMonthly)));

        // Coverage is checked per rent type, under the same filters, independently of the page asked for.
        List<RentEvidenceType> requested = type == null ? List.of(RentEvidenceType.values()) : List.of(type);
        List<RentEvidenceType> available = new ArrayList<>();
        List<RentEvidenceType> missing = new ArrayList<>();
        for (RentEvidenceType candidate : requested) {
            boolean any = observations.findGeography(level, normalisedCode, candidate, normalisedSource, rentPeriod,
                from, to, PageRequest.of(0, 1)).hasContent();
            (any ? available : missing).add(candidate);
        }
        Coverage coverage = available.isEmpty() ? Coverage.NO_COVERAGE
            : missing.isEmpty() ? Coverage.AVAILABLE : Coverage.PARTIAL;
        return new RentEvidenceResponseDTO(level, normalisedCode, coverage, available, missing,
            note(coverage, missing, level, normalisedCode), rows);
    }

    /** A sentence naming what is missing, or null when every requested rent type is covered. */
    private static String note(Coverage coverage, List<RentEvidenceType> missing, RentGeographyLevel level, String code) {
        if (coverage == Coverage.AVAILABLE) return null;
        String types = missing.stream().map(t -> t.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(" or "));
        return "No " + types + " rent evidence for " + level + " " + code + " under these filters.";
    }

    /** Maps a row; with {@code asMonthly}, an ANNUAL row's amounts are divided by 12 and the row is marked. */
    private static RentEvidenceObservationDTO toDto(RentEvidenceObservation row, boolean asMonthly) {
        boolean convert = asMonthly && row.getRentPeriod() == RentPeriod.ANNUAL;
        return new RentEvidenceObservationDTO(
            row.getId(),
            row.getEvidenceType(),
            row.getObservationKind(),
            row.getSource(),
            row.getSourceDataset(),
            row.getSourceReleaseDate(),
            row.getGeographyLevel(),
            row.getGeographyCode(),
            row.getGeographyName(),
            row.getState() == null ? null : row.getState().getId(),
            row.getCounty() == null ? null : row.getCounty().getId(),
            row.getPeriodStart(),
            row.getPeriodEnd(),
            row.getCurrency(),
            convert ? RentPeriod.MONTHLY : row.getRentPeriod(),
            convert ? RentPeriod.ANNUAL : null,
            row.getBedrooms(),
            monthly(row.getMedianRent(), convert, 2),
            monthly(row.getAverageRent(), convert, 2),
            monthly(row.getMedianRentPerSqft(), convert, 4),
            monthly(row.getMedianRentMoe(), convert, 2),
            row.getSampleCount(),
            row.getImportedAt(),
            row.getImportJobId());
    }

    private static BigDecimal monthly(BigDecimal amount, boolean convert, int scale) {
        if (amount == null || !convert) return amount;
        return amount.divide(MONTHS_PER_YEAR, scale, RoundingMode.HALF_UP);
    }

    private static String normaliseCode(RentGeographyLevel level, String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code is required");
        String value = code.trim();
        if (level == RentGeographyLevel.STATE) {
            if (!STATE_FORMAT.matcher(value).matches()) {
                throw new IllegalArgumentException("code: a STATE geography needs a two-letter state code");
            }
            return value.toUpperCase(Locale.ROOT);
        }
        if (!DIGITS.matcher(value).matches() || value.length() != level.codeLength()) {
            throw new IllegalArgumentException("code: a " + level + " geography needs a " + level.codeLength()
                + "-digit code");
        }
        return value;
    }
}
