package com.somil.jobportal.repository;

import com.somil.jobportal.entity.PasswordResetRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PasswordResetRequest p where p.email = :email")
    Optional<PasswordResetRequest> lockEmail(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PasswordResetRequest p where p.token = :token")
    Optional<PasswordResetRequest> lockToken(@Param("token") String token);
}
