package com.geointelli.ai.property.service.repository;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.geointelli.ai.property.service.entity.RoleGrantEvent;

/** Grant and revocation history of SCRUM-204 (table role_grant_events, V50). */
public interface RoleGrantEventRepository extends JpaRepository<RoleGrantEvent, Long> {

    /** Every grant and revocation of a user, newest first. */
    List<RoleGrantEvent> findByUserIdOrderByIdDesc(Long userId);

    /** The latest grant or revocation of one role of a user: a GRANT means GeoZentra currently gives it. */
    java.util.Optional<RoleGrantEvent> findFirstByUserIdAndRoleOrderByIdDesc(Long userId, String role);

    /**
     * Users whose latest realtor event is a GRANT by GeoZentra but who no longer have a verified license that is
     * valid today (no expiration date, or expiring today or later). Roles assigned by hand in Keycloak have no
     * event and are not returned.
     */
    @Query(value = """
            SELECT e.user_id FROM role_grant_events e
            WHERE e.role = 'realtor'
              AND e.id = (SELECT MAX(l.id) FROM role_grant_events l WHERE l.user_id = e.user_id AND l.role = 'realtor')
              AND e.action = 'GRANT'
              AND NOT EXISTS (SELECT 1 FROM realtor_license_verifications v
                              WHERE v.user_id = e.user_id AND v.status = 'VERIFIED'
                                AND (v.license_expiration_date IS NULL OR v.license_expiration_date >= :today))
            ORDER BY e.user_id
            """, nativeQuery = true)
    List<Long> findRealtorGrantsWithoutValidLicense(@Param("today") LocalDate today);
}
