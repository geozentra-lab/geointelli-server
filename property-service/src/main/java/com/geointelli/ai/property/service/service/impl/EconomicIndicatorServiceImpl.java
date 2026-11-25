package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.EconomicIndicatorDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationDTO;
import com.geointelli.ai.property.service.dto.EconomicIndicatorObservationsDTO;
import com.geointelli.ai.property.service.repository.EconomicIndicatorJdbcRepository;
import com.geointelli.ai.property.service.service.EconomicIndicatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Reads the economic indicator tables of migration V51 through {@link EconomicIndicatorJdbcRepository}. */
@Service
@RequiredArgsConstructor
public class EconomicIndicatorServiceImpl implements EconomicIndicatorService {

    private final EconomicIndicatorJdbcRepository repository;

    /** {@inheritDoc} */
    @Override
    public List<EconomicIndicatorDTO> getIndicators() {
        return repository.findIndicators();
    }

    /** {@inheritDoc} */
    @Override
    public EconomicIndicatorObservationsDTO getObservations(String code, String fips, Integer fromYear,
                                                            Integer toYear) {
        if (fips == null || !fips.matches("\\d{5}")) {
            throw new IllegalArgumentException("fips must be a 5-digit county FIPS code, for example 12086");
        }
        if (fromYear != null && toYear != null && fromYear > toYear) {
            throw new IllegalArgumentException("fromYear must not be after toYear");
        }
        EconomicIndicatorDTO indicator = repository.findIndicator(code.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown economic indicator: " + code));

        LocalDate from = fromYear == null ? null : LocalDate.of(fromYear, 1, 1);
        LocalDate to = toYear == null ? null : LocalDate.of(toYear, 12, 31);
        List<EconomicIndicatorObservationDTO> observations = repository.findObservations(indicator.code(), fips,
                "MONTHLY".equals(indicator.frequency()), from, to);
        return new EconomicIndicatorObservationsDTO(indicator.code(), indicator.unit(), indicator.frequency(),
                fips, fromYear, toYear,
                observations.isEmpty() ? EconomicIndicatorObservationsDTO.STATUS_EMPTY
                        : EconomicIndicatorObservationsDTO.STATUS_OK,
                observations);
    }
}
