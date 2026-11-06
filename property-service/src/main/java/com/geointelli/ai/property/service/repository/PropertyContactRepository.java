package com.geointelli.ai.property.service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.geointelli.ai.property.service.entity.PropertyContact;

public interface PropertyContactRepository extends JpaRepository<PropertyContact, Long> {
    Optional<PropertyContact> findByRequesterUserIdAndRequestKey(Long requesterUserId, String requestKey);
}

