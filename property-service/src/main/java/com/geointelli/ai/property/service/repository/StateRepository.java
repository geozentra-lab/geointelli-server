package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.State;

public interface StateRepository extends JpaRepository<State, Long> {
    Optional<State> findByCodeIgnoreCase(String code);
}
