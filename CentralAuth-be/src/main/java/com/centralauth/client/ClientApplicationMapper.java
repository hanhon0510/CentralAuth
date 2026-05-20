package com.centralauth.client;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClientApplicationMapper {

	void insert(ClientApplication clientApplication);

	Optional<ClientApplication> findByClientId(@Param("clientId") String clientId);

	List<ClientApplication> findAll();

	void insertRedirectUri(@Param("clientId") String clientId, @Param("redirectUri") String redirectUri);

	void deleteRedirectUris(@Param("clientId") String clientId);

	void insertAllowedOrigin(@Param("clientId") String clientId, @Param("origin") String origin);

	void deleteAllowedOrigins(@Param("clientId") String clientId);

	int updateClient(
			@Param("clientId") String clientId,
			@Param("clientName") String clientName,
			@Param("active") boolean active);

	int updateActive(@Param("clientId") String clientId, @Param("active") boolean active);
}
