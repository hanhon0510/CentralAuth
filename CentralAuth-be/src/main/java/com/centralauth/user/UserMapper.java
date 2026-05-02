package com.centralauth.user;

import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

	Optional<User> findByEmail(@Param("email") String email);

	Optional<User> findById(@Param("id") String id);

	void insert(User user);
}
