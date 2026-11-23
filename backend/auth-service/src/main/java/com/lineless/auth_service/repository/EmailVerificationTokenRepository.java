
package com.lineless.auth_service.repository;

import com.lineless.auth_service.entity.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM EmailVerificationToken token
            JOIN FETCH token.user
            WHERE token.tokenHash = :tokenHash
            """)
    Optional<EmailVerificationToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE EmailVerificationToken token
            SET token.usedAt = :now
            WHERE token.user.id = :userId
            AND token.usedAt IS NULL
            """)
    int invalidateUnusedTokensForUser(
            @Param("userId") UUID userId,
            @Param("now") OffsetDateTime now
    );
}
