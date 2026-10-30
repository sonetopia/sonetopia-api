package com.sonetopia.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sonetopia.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  @Modifying(clearAutomatically = true)
  @Query("""
    update RefreshToken t
    set t.revokedAt = :revokedAt
    where t.familyId = :familyId and t.revokedAt is null
    """)
  int revokeAllByFamilyId(
    @Param("familyId") UUID familyId,
    @Param("revokedAt") LocalDateTime revokedAt
  );

  @Modifying(clearAutomatically = true)
  @Query("""
    update RefreshToken t
    set t.revokedAt = :revokedAt
    where t.user.id = :userId and t.revokedAt is null
    """)
  int revokeAllByUserId(
    @Param("userId") UUID userId,
    @Param("revokedAt") LocalDateTime revokedAt
  );
}
