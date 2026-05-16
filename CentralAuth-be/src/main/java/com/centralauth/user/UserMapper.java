package com.centralauth.user;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

	Optional<User> findByEmail(@Param("email") String email);

	Optional<User> findById(@Param("id") String id);

	List<User> findAdminUsers(
			@Param("email") String email,
			@Param("accountStatus") AccountStatus accountStatus,
			@Param("limit") int limit);

	void insert(User user);

	void insertRole(@Param("userId") String userId, @Param("role") String role);

	List<String> findRolesByUserId(@Param("userId") String userId);

	int updateAccountStatus(
			@Param("id") String id,
			@Param("accountStatus") AccountStatus accountStatus,
			@Param("enabled") boolean enabled,
			@Param("emailVerified") boolean emailVerified);

	int verifyEmail(@Param("email") String email);

	int updatePasswordHash(@Param("id") String id, @Param("passwordHash") String passwordHash);
}
