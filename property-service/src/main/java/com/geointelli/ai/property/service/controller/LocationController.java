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

    @GetMapping("/states")
    public List<StateDTO> getStates() { return locationService.getStates(); }

    @GetMapping("/counties")
    public List<CountyLocationDTO> getCounties(@RequestParam Long stateId) {
        return locationService.getCounties(stateId);
    }
}
