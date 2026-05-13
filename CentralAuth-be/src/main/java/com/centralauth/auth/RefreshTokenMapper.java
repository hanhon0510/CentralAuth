package com.centralauth.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RefreshTokenMapper {

	void insert(RefreshToken refreshToken);

	Optional<RefreshToken> findByTokenHash(@Param("tokenHash") String tokenHash);

	List<RefreshToken> findByUserId(@Param("userId") String userId);

	int revoke(@Param("id") String id, @Param("revokedAt") Instant revokedAt);
}
