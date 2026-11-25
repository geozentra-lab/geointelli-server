package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.EconomicIndicatorImportResultDTO;
import com.geointelli.ai.property.service.dto.EconomicObservation;
import com.geointelli.ai.property.service.dto.EconomicProviderData;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository.RunCounts;
import com.geointelli.ai.property.service.service.BlsLausProviderService;
import com.geointelli.ai.property.service.service.CensusPopulationProviderService;
import com.geointelli.ai.property.service.service.EconomicIndicatorImportService;
import com.geointelli.ai.property.service.service.ReferenceDataScopeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates the request, reads the provider OUTSIDE any transaction (HTTP calls), then writes every value, counts
 * what changed and records the load in ONE transaction: an import is stored completely or not at all.
 */
@Slf4j
@Service
public class EconomicIndicatorImportServiceImpl implements EconomicIndicatorImportService {

    static final String SOURCE_BLS = "BLS_LAUS";
    static final String SOURCE_CENSUS = "CENSUS_PEP";
    /** First year of the BLS county series. */
    static final int FIRST_BLS_YEAR = 1990;
    /** Most years in one BLS import (limit of the API without a key). */
    static final int MAX_BLS_YEARS = 10;
    /** First vintage of the 2020s Census files. */
    static final int FIRST_CENSUS_VINTAGE = 2021;
    static final int MAX_SOURCE_MESSAGES = 20;
    static final String TABLE = "economic_indicator_observations";

    private final EconomicIndicatorJdbcRepository repository;
    private final ReferenceDataScopeService scopes;
    private final BlsLausProviderService blsLaus;
    private final CensusPopulationProviderService censusPopulation;
    private final TransactionTemplate transaction;
    private final Clock clock = Clock.systemUTC();

    public EconomicIndicatorImportServiceImpl(EconomicIndicatorJdbcRepository repository,
                                              ReferenceDataScopeService scopes,
                                              BlsLausProviderService blsLaus,
                                              CensusPopulationProviderService censusPopulation,
                                              PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.scopes = scopes;
        this.blsLaus = blsLaus;
        this.censusPopulation = censusPopulation;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** {@inheritDoc} */
    @Override
    public EconomicIndicatorImportResultDTO importBlsLaus(String state, String fips, Integer fromYear, Integer toYear,
                                                          String loadedBy) {
        String stateFips = stateFips(state);
        int currentYear = LocalDate.now(clock).getYear();
        int to = toYear == null ? currentYear : toYear;
        int from = fromYear == null ? to - 2 : fromYear;
        if (from < FIRST_BLS_YEAR) {
            throw new IllegalArgumentException("fromYear must be " + FIRST_BLS_YEAR + " or later");
        }
        if (to > currentYear) {
            throw new IllegalArgumentException("toYear must not be after " + currentYear);
        }
        if (from > to) {
            throw new IllegalArgumentException("fromYear must not be after toYear");
        }
        if (to - from + 1 > MAX_BLS_YEARS) {
            throw new IllegalArgumentException("The period must not be longer than " + MAX_BLS_YEARS + " years");
        }

        Map<String, String> counties = repository.findCounties(stateFips);
        if (fips != null) {
            if (!fips.matches("\\d{5}")) {
                throw new IllegalArgumentException("fips must be a 5-digit county FIPS code, for example 12086");
            }
            if (!counties.containsKey(fips)) {
                throw new IllegalArgumentException("fips " + fips + " is not a county of " + state);
            }
            counties = Map.of(fips, counties.get(fips));
        }
        if (counties.isEmpty()) {
            throw new IllegalArgumentException("No county of " + state + " has a FIPS code");
        }

        EconomicProviderData data = blsLaus.fetch(counties, from, to);
        return save(SOURCE_BLS, BlsLausProviderService.INDICATORS, stateFips, counties.size(), data, loadedBy);
    }

    /** {@inheritDoc} */
    @Override
    public EconomicIndicatorImportResultDTO importCensusPopulation(String state, Integer vintage, String loadedBy) {
        String stateFips = stateFips(state);
        int currentYear = LocalDate.now(clock).getYear();
        int year = vintage == null ? currentYear - 1 : vintage;
        if (year < FIRST_CENSUS_VINTAGE || year > currentYear) {
            throw new IllegalArgumentException("vintage must be between " + FIRST_CENSUS_VINTAGE + " and " + currentYear);
        }

        EconomicProviderData data = censusPopulation.fetch(stateFips, year);
        int counties = (int) data.observations().stream().map(EconomicObservation::geographyCode).distinct().count();
        return save(SOURCE_CENSUS, CensusPopulationProviderService.INDICATORS, stateFips, counties, data, loadedBy);
    }

    /** The 2-digit FIPS code of the state; 409 when migration V50 has not been applied. */
    private String stateFips(String state) {
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state is required, for example FL");
        }
        if (!scopes.hasTable(TABLE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The table " + TABLE + " is missing: apply migration V50 first");
        }
        return scopes.stateFips(state).orElseThrow(() ->
                new IllegalArgumentException("Unknown state, or none of its counties has a FIPS code: " + state));
    }

    /** Writes the values, counts what changed and records the load, in one transaction. */
    private EconomicIndicatorImportResultDTO save(String source, List<String> codes, String stateFips, int counties,
                                                  EconomicProviderData data, String loadedBy) {
        List<EconomicObservation> observations = data.observations();
        requireOnePerPeriod(source, observations);
        // PostgreSQL keeps microseconds: the rows of this import are found again by this exact time
        Instant importedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        OffsetDateTime now = importedAt.atOffset(ZoneOffset.UTC);
        LocalDate periodFrom = observations.stream().map(EconomicObservation::periodStart)
                .min(Comparator.naturalOrder()).orElse(null);
        LocalDate periodTo = observations.stream().map(EconomicObservation::periodEnd)
                .max(Comparator.naturalOrder()).orElse(null);
        List<String> messages = data.sourceMessages().stream().limit(MAX_SOURCE_MESSAGES).toList();

        EconomicIndicatorImportResultDTO result = transaction.execute(status -> {
            repository.upsert(observations, data.sourceVintage(), data.sourceUrl(), now);
            RunCounts counts = repository.countRun(codes, now);
            int received = observations.size();
            EconomicIndicatorImportResultDTO imported = new EconomicIndicatorImportResultDTO(source, stateFips,
                    counties, data.sourceUrl(), data.sourceVintage(), periodFrom, periodTo, received,
                    counts.inserted(), counts.revised(), counts.written() - counts.inserted() - counts.revised(),
                    received - counts.written(), data.missingValues(), messages, importedAt);
            repository.recordLoad(imported, loadedBy);
            return imported;
        });
        log.info("Economic indicators {} state {} vintage {}: {} received, {} inserted, {} revised, {} unchanged, "
                        + "{} older vintage, {} missing", source, stateFips, data.sourceVintage(), result.received(),
                result.inserted(), result.revised(), result.unchanged(), result.olderVintage(), result.missingValues());
        return result;
    }

    /** A source giving the same indicator, county and period twice would be counted wrongly; refuse it. */
    private static void requireOnePerPeriod(String source, List<EconomicObservation> observations) {
        Set<String> keys = new HashSet<>();
        for (EconomicObservation observation : observations) {
            String key = observation.indicatorCode() + " " + observation.geographyCode() + " "
                    + observation.periodStart();
            if (!keys.add(key)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        source + " returned the same period twice: " + key);
            }
        }
    }
}
