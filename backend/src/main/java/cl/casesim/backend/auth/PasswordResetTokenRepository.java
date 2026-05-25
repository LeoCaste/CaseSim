package cl.casesim.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    @Modifying
    @Query("""
            update PasswordResetToken t
               set t.usedAt = :now
             where t.user.id = :userId
               and t.usedAt is null
               and t.expiresAt > :now
            """)
    int invalidateActiveTokens(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    @Query("""
            select t
              from PasswordResetToken t
             where t.tokenHash = :tokenHash
               and t.usedAt is null
               and t.expiresAt > :now
            """)
    Optional<PasswordResetToken> findValidByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);
}
