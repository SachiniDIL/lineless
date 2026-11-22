package com.lineless.auth_service.repository;

import com.lineless.auth_service.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM PasswordResetToken t
            WHERE t.tokenHash = :tokenHash
            """)
    Optional<PasswordResetToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE PasswordResetToken t
            SET t.usedAt = :usedAt
            WHERE t.user.id = :userId
              AND t.usedAt IS NULL
            """)
    int invalidateUnusedTokensByUserId(
            @Param("userId") UUID userId,
            @Param("usedAt") OffsetDateTime usedAt
    );
}