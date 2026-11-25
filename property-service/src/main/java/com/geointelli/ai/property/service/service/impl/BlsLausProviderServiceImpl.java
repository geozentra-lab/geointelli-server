package com.geointelli.ai.property.service.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geointelli.ai.property.service.dto.EconomicObservation;
import com.geointelli.ai.property.service.dto.EconomicProviderData;
import com.geointelli.ai.property.service.service.BlsLausProviderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the BLS public data API. Without a key (v1) a request takes 25 series and the API allows 25 requests a day;
 * with BLS_API_KEY (v2, free registration) 50 series and 500 requests a day. Series IDs are LAUCN + county FIPS +
 * 00000000 + measure: 03 unemployment rate, 05 employment, 06 labor force (LAUCN120860000000003 = Miami-Dade
 * unemployment rate).
 */
@Slf4j
@Service
public class BlsLausProviderServiceImpl implements BlsLausProviderService {

    static final String URL_V1 = "https://api.bls.gov/publicAPI/v1/timeseries/data/";
    static final String URL_V2 = "https://api.bls.gov/publicAPI/v2/timeseries/data/";
    /** BLS measure code (last 2 digits of the series ID) to indicator code. */
    static final Map<String, String> MEASURES = Map.of(
            "06", "LABOR_FORCE",
            "05", "EMPLOYMENT",
            "03", "UNEMPLOYMENT_RATE");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public BlsLausProviderServiceImpl(ObjectMapper objectMapper,
                                      @Value("${economic-indicators.bls.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(60_000);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    /** {@inheritDoc} */
    @Override
    public EconomicProviderData fetch(Map<String, String> counties, int fromYear, int toYear) {
        String url = apiKey.isEmpty() ? URL_V1 : URL_V2;
        int seriesPerRequest = apiKey.isEmpty() ? 25 : 50;

        List<String> seriesIds = new ArrayList<>();
        for (String fips : counties.keySet()) {
            for (String measure : MEASURES.keySet()) {
                seriesIds.add("LAUCN" + fips + "00000000" + measure);
            }
        }

        List<EconomicObservation> observations = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int missingValues = 0;
        for (int from = 0; from < seriesIds.size(); from += seriesPerRequest) {
            List<String> batch = seriesIds.subList(from, Math.min(from + seriesPerRequest, seriesIds.size()));
            JsonNode root = post(url, batch, fromYear, toYear);
            missingValues += read(root, counties, observations, messages);
        }
        log.info("BLS LAUS: {} series, {} to {}: {} values, {} missing", seriesIds.size(), fromYear, toYear,
                observations.size(), missingValues);
        return new EconomicProviderData(url, LocalDate.now(ZoneOffset.UTC).toString(), observations, missingValues,
                messages);
    }

    /**
     * Reads one API answer: adds its monthly values to observations and its messages to messages.
     *
     * @param counties county name by FIPS code, for the geography name
     * @return how many values the answer did not give ("-")
     */
    static int read(JsonNode root, Map<String, String> counties, List<EconomicObservation> observations,
                    List<String> messages) {
        int missingValues = 0;
        for (JsonNode message : root.path("message")) {
            messages.add(message.asText());
        }
        for (JsonNode series : root.path("Results").path("series")) {
            String seriesId = series.path("seriesID").asText();
            String fips = seriesId.substring(5, 10);
            String indicatorCode = MEASURES.get(seriesId.substring(seriesId.length() - 2));
            for (JsonNode item : series.path("data")) {
                String period = item.path("period").asText();
                // M01 to M12 are months; M13 is the annual average, not stored with the months
                if (!period.matches("M(0[1-9]|1[0-2])")) {
                    continue;
                }
                BigDecimal value = number(item.path("value").asText());
                if (value == null) {
                    missingValues++;
                    continue;
                }
                YearMonth month = YearMonth.of(item.path("year").asInt(), Integer.parseInt(period.substring(1)));
                // BLS publishes county LAUS values as model-based estimates, preliminary (footnote P) or not
                observations.add(new EconomicObservation(indicatorCode, fips, counties.get(fips),
                        month.atDay(1), month.atEndOfMonth(), value, EconomicObservation.ESTIMATE, seriesId));
            }
        }
        return missingValues;
    }

    /** One API request; the API answers 200 even when it refuses it, with a status other than REQUEST_SUCCEEDED. */
    private JsonNode post(String url, List<String> seriesIds, int fromYear, int toYear) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("seriesid", seriesIds);
        body.put("startyear", String.valueOf(fromYear));
        body.put("endyear", String.valueOf(toYear));
        if (!apiKey.isEmpty()) {
            body.put("registrationkey", apiKey);
        }
        String response;
        JsonNode root;
        try {
            response = restClient.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            root = objectMapper.readTree(response == null ? "" : response);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "BLS API unavailable: " + e.getMessage(), e);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "BLS API returned an unreadable response", e);
        }
        String status = root.path("status").asText();
        if (!"REQUEST_SUCCEEDED".equals(status)) {
            List<String> messages = new ArrayList<>();
            root.path("message").forEach(message -> messages.add(message.asText()));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "BLS API refused the request (" + status + "): " + String.join("; ", messages));
        }
        return root;
    }

    /** The value, or null when the API gives none ("-") or something that is not a number. */
    private static BigDecimal number(String text) {
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
