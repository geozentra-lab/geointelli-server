package com.geointelli.ai.property.service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Assessment;

@Repository 
public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

}
