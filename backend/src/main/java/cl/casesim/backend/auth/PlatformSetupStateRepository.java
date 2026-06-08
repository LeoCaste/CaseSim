package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface PlatformSetupStateRepository extends JpaRepository<PlatformSetupState, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PlatformSetupState s where s.id = :id")
    Optional<PlatformSetupState> findByIdForUpdate(@Param("id") Long id);
}
