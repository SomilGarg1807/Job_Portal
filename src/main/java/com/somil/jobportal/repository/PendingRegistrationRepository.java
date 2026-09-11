package com.somil.jobportal.repository;

import com.somil.jobportal.entity.PendingRegistration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.email = :email")
    Optional<PendingRegistration> lockEmail(@Param("email") String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.token = :token")
    Optional<PendingRegistration> lockToken(@Param("token") String token);
}
