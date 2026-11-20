package com.lineless.auth_service.repository;

import com.lineless.auth_service.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM RefreshToken r
            WHERE r.tokenHash = :tokenHash
            """)
    Optional<RefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash
    );

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = :revokedAt
            WHERE r.familyId = :familyId
              AND r.revokedAt IS NULL
            """)
    int revokeActiveTokensByFamilyId(
            @Param("familyId") UUID familyId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );

    @Modifying
    @Query("""
            UPDATE RefreshToken r
            SET r.revokedAt = :revokedAt
            WHERE r.user.id = :userId
              AND r.revokedAt IS NULL
            """)
    int revokeAllActiveTokensByUserId(
            @Param("userId") UUID userId,
            @Param("revokedAt") OffsetDateTime revokedAt
    );
}
