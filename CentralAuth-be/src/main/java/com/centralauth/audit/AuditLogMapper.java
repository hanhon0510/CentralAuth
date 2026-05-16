package com.centralauth.audit;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditLogMapper {

	void insert(AuditLog auditLog);

	List<AuditLog> findRecent(
			@Param("eventType") String eventType,
			@Param("userId") String userId,
			@Param("email") String email,
			@Param("limit") int limit);
}
