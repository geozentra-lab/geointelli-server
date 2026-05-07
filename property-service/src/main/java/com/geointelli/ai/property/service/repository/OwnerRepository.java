package com.geointelli.ai.property.service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.geointelli.ai.property.service.entity.Owner;

@Repository
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    List<Owner> findAllByNameAndTenancyCdAndRole(String name, String tenancyCd, String role);
}
