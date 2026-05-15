package com.centralauth.user;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

	Optional<User> findByEmail(@Param("email") String email);

	Optional<User> findById(@Param("id") String id);

	void insert(User user);

	void insertRole(@Param("userId") String userId, @Param("role") String role);

	int verifyEmail(@Param("email") String email);

	int updatePasswordHash(@Param("id") String id, @Param("passwordHash") String passwordHash);
}
