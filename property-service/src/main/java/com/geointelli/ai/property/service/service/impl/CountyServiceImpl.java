package com.geointelli.ai.property.service.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.service.CountyService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CountyServiceImpl implements CountyService {
    private final CountyRepository countyRepository;

    private static final String[][] US_STATES = {
        {"AL", "Alabama"}, {"AK", "Alaska"}, {"AZ", "Arizona"}, {"AR", "Arkansas"},
        {"CA", "California"}, {"CO", "Colorado"}, {"CT", "Connecticut"}, {"DE", "Delaware"},
        {"FL", "Florida"}, {"GA", "Georgia"}, {"HI", "Hawaii"}, {"ID", "Idaho"},
        {"IL", "Illinois"}, {"IN", "Indiana"}, {"IA", "Iowa"}, {"KS", "Kansas"},
        {"KY", "Kentucky"}, {"LA", "Louisiana"}, {"ME", "Maine"}, {"MD", "Maryland"},
        {"MA", "Massachusetts"}, {"MI", "Michigan"}, {"MN", "Minnesota"}, {"MS", "Mississippi"},
        {"MO", "Missouri"}, {"MT", "Montana"}, {"NE", "Nebraska"}, {"NV", "Nevada"},
        {"NH", "New Hampshire"}, {"NJ", "New Jersey"}, {"NM", "New Mexico"}, {"NY", "New York"},
        {"NC", "North Carolina"}, {"ND", "North Dakota"}, {"OH", "Ohio"}, {"OK", "Oklahoma"},
        {"OR", "Oregon"}, {"PA", "Pennsylvania"}, {"RI", "Rhode Island"}, {"SC", "South Carolina"},
        {"SD", "South Dakota"}, {"TN", "Tennessee"}, {"TX", "Texas"}, {"UT", "Utah"},
        {"VT", "Vermont"}, {"VA", "Virginia"}, {"WA", "Washington"}, {"WV", "West Virginia"},
        {"WI", "Wisconsin"}, {"WY", "Wyoming"}, {"DC", "District of Columbia"},
        {"AS", "American Samoa"}, {"GU", "Guam"}, {"MP", "Northern Mariana Islands"},
        {"PR", "Puerto Rico"}, {"VI", "U.S. Virgin Islands"}
    };

    @Override
    @Transactional
    public void ensureExists(String county, String state) {
        if (county == null || county.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException("County and state are required");
        }
        String countyName = county.trim();
        String stateInput = state.trim();
        if (countyName.length() > 100) {
            throw new IllegalArgumentException("County name must not exceed 100 characters");
        }
        if (countyRepository.findByCountyAndState(countyName, stateInput).isPresent()) {
            return;
        }
        for (String[] entry : US_STATES) {
            if (entry[0].equalsIgnoreCase(stateInput) || entry[1].equalsIgnoreCase(stateInput)) {
                countyRepository.insertStateIfAbsent(entry[0], entry[1]);
                countyRepository.insertCountyIfAbsent(countyName, entry[0]);
                resolve(countyName, stateInput);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown U.S. state: " + state);
    }

    @Override
    public County resolve(String county, String state) {
        if (county == null || county.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException("County and state are required");
        }
        return countyRepository.findByCountyAndState(county.trim(), state.trim())
            .orElseThrow(() -> new IllegalArgumentException(
                "Configure county '" + county + "' in state '" + state + "' before importing properties"));
    }

    @Override
    public County miamiDade() {
        return resolve("Miami-Dade", "FL");
    }
}
