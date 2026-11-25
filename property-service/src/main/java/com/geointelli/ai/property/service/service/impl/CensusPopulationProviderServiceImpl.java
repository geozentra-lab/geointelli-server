package com.geointelli.ai.property.service.service.impl;

import com.geointelli.ai.property.service.dto.EconomicObservation;
import com.geointelli.ai.property.service.dto.EconomicProviderData;
import com.geointelli.ai.property.service.service.CensusPopulationProviderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Downloads co-est{vintage}-alldata.csv from www2.census.gov (one row per county of the United States, one column
 * per year: POPESTIMATE2024, NETMIG2024 ...) and keeps the counties (SUMLEV 050) of one state.
 */
@Slf4j
@Service
public class CensusPopulationProviderServiceImpl implements CensusPopulationProviderService {

    static final String URL =
            "https://www2.census.gov/programs-surveys/popest/datasets/2020-%d/counties/totals/co-est%d-alldata.csv";
    static final int FIRST_YEAR = 2020;
    static final String COUNTY_LEVEL = "050";
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get();

    private final RestClient restClient;

    public CensusPopulationProviderServiceImpl() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(60_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** {@inheritDoc} */
    @Override
    public EconomicProviderData fetch(String stateFips, int vintage) {
        String url = String.format(URL, vintage, vintage);
        byte[] file;
        try {
            file = restClient.get().uri(url).retrieve().body(byte[].class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("The Census Bureau has not published the vintage " + vintage);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Census file unavailable: " + e.getMessage(), e);
        }
        if (file == null || file.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The Census file is empty: " + url);
        }

        List<EconomicObservation> observations = new ArrayList<>();
        int missingValues = read(file, stateFips, vintage, url, observations);
        log.info("Census population vintage {}, state {}: {} values, {} missing", vintage, stateFips,
                observations.size(), missingValues);
        return new EconomicProviderData(url, String.valueOf(vintage), observations, missingValues, List.of());
    }

    /**
     * Reads the file: adds the values of the counties of the state to observations.
     *
     * @param url where the file was read, for the error messages
     * @return how many cells were empty or not a number
     * @throws ResponseStatusException 502 when the file is not the expected file
     */
    static int read(byte[] file, String stateFips, int vintage, String url, List<EconomicObservation> observations) {
        int[] missingValues = new int[1];
        // The Census files are Latin-1 (Doña Ana County)
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(file), StandardCharsets.ISO_8859_1);
             CSVParser parser = FORMAT.parse(reader)) {
            List<String> headers = parser.getHeaderNames();
            for (String column : List.of("SUMLEV", "STATE", "COUNTY", "CTYNAME", "POPESTIMATE" + vintage,
                    "NETMIG" + vintage, "DOMESTICMIG" + vintage, "INTERNATIONALMIG" + vintage)) {
                if (!headers.contains(column)) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "The Census file has no column " + column + ": " + url);
                }
            }
            for (CSVRecord row : parser) {
                if (!COUNTY_LEVEL.equals(row.get("SUMLEV")) || !stateFips.equals(row.get("STATE"))) {
                    continue;
                }
                String fips = row.get("STATE") + row.get("COUNTY");
                String name = row.get("CTYNAME");
                for (int year = FIRST_YEAR; year <= vintage; year++) {
                    // Population on July 1
                    LocalDate july1 = LocalDate.of(year, 7, 1);
                    add(observations, missingValues, row, "POPESTIMATE" + year, "POPULATION", fips, name, july1, july1);
                    if (year == FIRST_YEAR) {
                        continue; // 2020 migration covers April 1 to June 30 only
                    }
                    // Migration from July 1 of the previous year to June 30
                    LocalDate start = LocalDate.of(year - 1, 7, 1);
                    LocalDate end = LocalDate.of(year, 6, 30);
                    add(observations, missingValues, row, "NETMIG" + year, "NET_MIGRATION", fips, name, start, end);
                    add(observations, missingValues, row, "DOMESTICMIG" + year, "NET_DOMESTIC_MIGRATION", fips, name,
                            start, end);
                    add(observations, missingValues, row, "INTERNATIONALMIG" + year, "NET_INTERNATIONAL_MIGRATION",
                            fips, name, start, end);
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "The Census file cannot be read: " + url, e);
        }
        return missingValues[0];
    }

    /** Adds the value of one column; an empty or non-numeric cell is counted as missing. */
    private static void add(List<EconomicObservation> observations, int[] missingValues, CSVRecord row, String column,
                            String indicatorCode, String fips, String name, LocalDate start, LocalDate end) {
        String text = row.isMapped(column) && row.isSet(column) ? row.get(column).trim() : "";
        BigDecimal value;
        try {
            value = text.isEmpty() ? null : new BigDecimal(text);
        } catch (NumberFormatException e) {
            value = null;
        }
        if (value == null) {
            missingValues[0]++;
            return;
        }
        // The Census Bureau publishes these figures as estimates
        observations.add(new EconomicObservation(indicatorCode, fips, name, start, end, value,
                EconomicObservation.ESTIMATE, column));
    }
}
