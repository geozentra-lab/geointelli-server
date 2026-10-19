package com.geointelli.ai.property.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.geointelli.ai.property.service.entity.Building;

public interface BuildingRepository extends JpaRepository<Building, Long> {

}
