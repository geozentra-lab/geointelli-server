package com.geointelli.ai.property.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.UserPreferences;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
}
