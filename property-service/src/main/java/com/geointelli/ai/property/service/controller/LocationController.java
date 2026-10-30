package com.geointelli.ai.property.service.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.geointelli.ai.property.service.dto.CountyLocationDTO;
import com.geointelli.ai.property.service.dto.StateDTO;
import com.geointelli.ai.property.service.service.LocationService;
import lombok.RequiredArgsConstructor;

/** Supplies state and county options for user preference forms. */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locationService;

    /**
     * Lists every supported state, sorted by name.
     *
     * @return the states as {@code id}, {@code code} and {@code name}; empty when none are registered
     */
    @GetMapping("/states")
    public List<StateDTO> getStates() { return locationService.getStates(); }

    /**
     * Lists the counties of one supported state, sorted by name.
     *
     * @param stateId id of the state returned by {@link #getStates()}; required
     * @return the counties of that state; empty when the state has none
     * @throws org.springframework.web.server.ResponseStatusException 404 when the state does not exist
     */
    @GetMapping("/counties")
    public List<CountyLocationDTO> getCounties(@RequestParam Long stateId) {
        return locationService.getCounties(stateId);
    }
}
