package com.centralauth.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.centralauth")
public class MyBatisConfig {
}
