package com.geointelli.ai.property.service.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.geointelli.ai.property.service.dto.UserDTO;
import com.geointelli.ai.property.service.dto.UserPreferencesDTO;
import com.geointelli.ai.property.service.dto.UserProfileUpdateRequest;
import com.geointelli.ai.property.service.dto.CountyLocationDTO;
import com.geointelli.ai.property.service.entity.County;
import com.geointelli.ai.property.service.entity.User;
import com.geointelli.ai.property.service.entity.UserPreferences;
import com.geointelli.ai.property.service.mapper.UserMapper;
import com.geointelli.ai.property.service.mapper.LocationMapper;
import com.geointelli.ai.property.service.repository.CountyRepository;
import com.geointelli.ai.property.service.repository.UserRepository;
import com.geointelli.ai.property.service.repository.UserPreferencesRepository;
import com.geointelli.ai.property.service.service.CurrentUserService;
import com.geointelli.ai.property.service.service.UserProfileService;
import lombok.RequiredArgsConstructor;

/** Persists profile changes and preferences for the user identified by the bearer token. */
@Service
@RequiredArgsConstructor
@Transactional
public class UserProfileServiceImpl implements UserProfileService {
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final UserMapper userMapper;
    private final CountyRepository countyRepository;
    private final LocationMapper locationMapper;

    @Override
    public UserDTO updateProfile(UserProfileUpdateRequest request) {
        User user = currentUserService.getCurrentUserEntity();
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName().trim());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName().trim());
        }
        if (request.getPersona() != null) {
            user.setPersona(request.getPersona());
        }
        return userMapper.toDTO(userRepository.save(user));
    }

    @Override
    public UserPreferencesDTO getPreferences() {
        Long userId = currentUserService.getCurrentUserEntity().getId();
        return preferencesRepository.findById(userId)
            .map(this::toDTO).orElseGet(UserPreferencesDTO::new);
    }

    @Override
    public UserPreferencesDTO updatePreferences(UserPreferencesDTO request) {
        if (request.getMinBudget() != null && request.getMaxBudget() != null
                && request.getMinBudget().compareTo(request.getMaxBudget()) > 0) {
            throw new IllegalArgumentException("minBudget must not exceed maxBudget");
        }
        List<County> targetCounties = resolveCounties(request.getTargetLocations());
        Long userId = currentUserService.getCurrentUserEntity().getId();
        UserPreferences preferences = preferencesRepository.findById(userId)
            .orElseGet(UserPreferences::new);
        preferences.setUserId(userId);
        preferences.setMinBudget(request.getMinBudget());
        preferences.setMaxBudget(request.getMaxBudget());
        preferences.getTargetCounties().clear();
        preferences.getTargetCounties().addAll(targetCounties);
        preferences.getInvestmentGoals().clear();
        preferences.getInvestmentGoals().addAll(normalize(request.getInvestmentGoals()));
        return toDTO(preferencesRepository.save(preferences));
    }

    /** Requires existing counties and removes repeated IDs while preserving selection order. */
    private List<County> resolveCounties(List<CountyLocationDTO> locations) {
        List<Long> ids = new ArrayList<>();
        for (CountyLocationDTO location : locations) {
            if (!ids.contains(location.getCountyId())) ids.add(location.getCountyId());
        }
        if (ids.isEmpty()) return new ArrayList<>();
        Map<Long, County> byId = new HashMap<>();
        for (County county : countyRepository.findByIdIn(ids)) byId.put(county.getId(), county);
        List<County> result = new ArrayList<>();
        for (Long id : ids) {
            County county = byId.get(id);
            if (county == null) throw new IllegalArgumentException("Unknown countyId: " + id);
            result.add(county);
        }
        return result;
    }

    /** Trims values and removes repeated entries while preserving their order. */
    private List<String> normalize(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String trimmed = value.trim();
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** Copies persisted preferences into an API response. */
    private UserPreferencesDTO toDTO(UserPreferences preferences) {
        UserPreferencesDTO result = new UserPreferencesDTO();
        result.setMinBudget(preferences.getMinBudget());
        result.setMaxBudget(preferences.getMaxBudget());
        result.setTargetLocations(preferences.getTargetCounties().stream().map(locationMapper::toDTO).toList());
        result.setInvestmentGoals(new ArrayList<>(preferences.getInvestmentGoals()));
        return result;
    }
}
